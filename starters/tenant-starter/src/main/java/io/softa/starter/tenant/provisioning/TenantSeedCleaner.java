package io.softa.starter.tenant.provisioning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.utils.IdUtils;

/**
 * Lets a seeder discard its own previous output before seeding again, so re-running provisioning produces a
 * clean result instead of colliding with what the last run left behind.
 *
 * <h3>Why every seeder cleans up after itself instead of one service cleaning up after all of them</h3>
 * A central purge has to reach into every module's tables. That works in one process and stops working the
 * moment a module becomes its own service: the purge would still compile, still run, still report success, and
 * simply not delete anything belonging to the service that moved out. Silent, and it fails at deployment time
 * rather than at build time.
 *
 * <p>Seeding is already distributed this way — each seeder subscribes to the provisioning broadcast and writes
 * its own data — so discarding is modelled the same way. A seeder that can seed can clean, wherever it runs,
 * and the two live in one class so neither can be updated without the other in view.
 *
 * <h3>Why no coordination is needed</h3>
 * Clearing happens <b>inside</b> the same message handling as the re-seed, so a service's own purge and seed
 * are serialized by construction: there is no window in which one service re-seeds while another is still
 * deleting, and therefore no "everyone finished purging" latch to build. It is also self-correcting under
 * at-least-once delivery — a redelivered message clears only that seeder's rows and writes them again, never
 * touching another seeder's.
 */
@Slf4j
@Service
public class TenantSeedCleaner {

    /** The predefined-data ledger: one row per seeded row, carrying the model and the row id it created. */
    private static final String PRE_DATA_LEDGER = "SysPreData";

    private final ModelService<Long> modelService;

    public TenantSeedCleaner(ModelService<Long> modelService) {
        this.modelService = modelService;
    }

    /**
     * Delete this tenant's rows of the given models, in the order supplied.
     *
     * <p>The order is the caller's responsibility and must be <b>child-first</b>: only the seeder knows how its
     * rows reference each other, and reversing it leaves a child pointing at a parent that is gone — or fails
     * the delete outright once any of those FKs declares {@code onDelete = RESTRICT}.
     *
     * <p>A no-op on a tenant seeded for the first time, so a seeder can call it unconditionally.
     *
     * @param tenantId   tenant whose rows to remove
     * @param modelNames the seeder's own models, child-first
     * @return rows deleted per model, for the caller to log — a delete that reports nothing cannot be checked
     */
    public Map<String, Integer> clearModels(Long tenantId, List<String> modelNames) {
        Map<String, Integer> deleted = new LinkedHashMap<>();
        for (String modelName : modelNames) {
            List<Long> ids = modelService.getIds(modelName,
                    new Filters().eq(ModelConstant.TENANT_ID, tenantId));
            if (!ids.isEmpty()) {
                modelService.deleteByIds(modelName, ids);
                deleted.put(modelName, ids.size());
            }
        }
        return deleted;
    }

    /**
     * Delete the PROFILES of this tenant's accounts — resolved through the accounts, because a
     * person is a global model with no tenant column of its own. Filtering {@code UserProfile} by
     * {@code tenantId} does not merely return nothing, it throws: the field no longer exists.
     *
     * <p>Must run BEFORE the accounts are cleared — the accounts are the only route to these rows,
     * and once they are gone the profiles are unreachable orphans.
     */
    public int clearProfilesOf(Long tenantId) {
        List<Long> accountIds = modelService.getIds("UserAccount",
                new Filters().eq(ModelConstant.TENANT_ID, tenantId));
        if (accountIds.isEmpty()) {
            return 0;
        }
        List<Long> profileIds = modelService.getIds("UserProfile",
                new Filters().in("userId", accountIds));
        if (!profileIds.isEmpty()) {
            modelService.deleteByIds("UserProfile", profileIds);
        }
        return profileIds.size();
    }

    /**
     * Delete everything this tenant's predefined-data load created, then the bindings that recorded it.
     *
     * <p>Driven by the ledger rather than by the seed file list, which is what makes it exact: the loader
     * writes one binding per row it creates — including rows nested inside a parent's JSON, which appear under
     * their own model — so the ledger already covers children that no file names at top level, and stays
     * correct when a seed file is added, removed or renamed.
     *
     * <p>It is also the only way to remove an <b>orphan</b>: the loader reconciles create-or-update by business
     * key, so a row it used to create and no longer does would otherwise survive every re-run forever.
     *
     * <p>The bindings go too. Left behind, they would point the next load at rows that no longer exist, and its
     * create-or-update reconciliation would try to update them.
     *
     * @param tenantId tenant whose predefined data to remove
     * @return rows deleted per model, including the ledger itself
     */
    public Map<String, Integer> clearPreData(Long tenantId) {
        List<Map<String, Object>> bindings = modelService.searchList(PRE_DATA_LEDGER,
                new FlexQuery(new Filters().eq(ModelConstant.TENANT_ID, tenantId)));
        if (bindings.isEmpty()) {
            return Map.of();
        }
        // rowId is stored as text whatever the model's key really is, so it has to be converted back per model
        // before it can address a row. Reading it as a number looks right for the common case and matches
        // nothing — every id arrives as a String, so the whole clear would delete zero rows and report zero,
        // which is indistinguishable from "there was nothing to clear".
        Map<String, List<String>> rowIdsByModel = new LinkedHashMap<>();
        List<String> bindingIds = new ArrayList<>(bindings.size());
        for (Map<String, Object> binding : bindings) {
            bindingIds.add(String.valueOf(binding.get("id")));
            Object rowId = binding.get("rowId");
            String model = (String) binding.get("model");
            if (rowId != null && model != null) {
                rowIdsByModel.computeIfAbsent(model, k -> new ArrayList<>()).add(rowId.toString());
            }
        }
        Map<String, Integer> deleted = new LinkedHashMap<>();
        rowIdsByModel.forEach((model, rowIds) -> {
            modelService.deleteByIds(model, toRowIds(model, rowIds));
            deleted.put(model, rowIds.size());
        });
        modelService.deleteByIds(PRE_DATA_LEDGER, toRowIds(PRE_DATA_LEDGER, bindingIds));
        deleted.put(PRE_DATA_LEDGER, bindingIds.size());
        return deleted;
    }

    /**
     * Turn the ledger's textual row ids back into ids of the model's own key type.
     *
     * <p>Package-private and overridable so the collection above stays unit-testable: the conversion resolves
     * the model's primary-key field out of {@code ModelManager}, which a plain unit test has no snapshot for.
     */
    @SuppressWarnings("unchecked")
    List<Long> toRowIds(String modelName, List<String> rowIds) {
        return (List<Long>) (List<?>) IdUtils.formatIds(modelName, rowIds);
    }
}
