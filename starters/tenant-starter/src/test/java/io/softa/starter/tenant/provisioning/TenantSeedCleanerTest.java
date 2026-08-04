package io.softa.starter.tenant.provisioning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How a seeder discards its own previous output before seeding again.
 *
 * <p>Each seeder cleaning up after itself is what keeps a rebuild working once a module becomes its own
 * service: a central sweep would still compile and still report success while quietly missing everything
 * belonging to the service that moved out. So the unit under test is deliberately small and per-caller — it
 * deletes exactly the models it is handed, and never goes looking for more.
 */
class TenantSeedCleanerTest {

    private static final long TENANT = 1001L;

    private ModelService<Long> modelService;
    private TenantSeedCleaner cleaner;
    private List<Map<String, Object>> bindings;
    private List<Map.Entry<String, List<?>>> deletes;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        modelService = mock(ModelService.class);
        bindings = new ArrayList<>();
        deletes = new ArrayList<>();

        when(modelService.getIds(anyString(), any(Filters.class))).thenReturn(List.of(11L, 12L));
        when(modelService.searchList(eq("SysPreData"), any(FlexQuery.class)))
                .thenAnswer(inv -> new ArrayList<>(bindings));
        when(modelService.deleteByIds(anyString(), anyList())).thenAnswer(inv -> {
            deletes.add(Map.entry(inv.getArgument(0), inv.getArgument(1)));
            return true;
        });

        // The id conversion resolves each model's primary-key type out of ModelManager, which a plain unit test
        // has no metadata snapshot for. Everything these cases are about happens before it, so it is stubbed to
        // a pass-through rather than worked around.
        cleaner = new TenantSeedCleaner(modelService) {
            @Override
            List<Long> toRowIds(String modelName, List<String> rowIds) {
                return rowIds.stream().map(Long::valueOf).toList();
            }
        };
    }

    // ─── clearing a seeder's declared models ───

    @Test
    @DisplayName("only the models it was handed, in the order it handed them")
    void clearsExactlyWhatItWasGiven() {
        // The order is child-first and the caller's responsibility: reversing it leaves a child pointing at a
        // parent that is gone, or fails outright once one of those FKs declares onDelete = RESTRICT.
        cleaner.clearModels(TENANT, List.of("Department", "CostCentre", "LegalEntity"));

        InOrder order = inOrder(modelService);
        order.verify(modelService).deleteByIds(eq("Department"), anyList());
        order.verify(modelService).deleteByIds(eq("CostCentre"), anyList());
        order.verify(modelService).deleteByIds(eq("LegalEntity"), anyList());
        assertThat(modelsDeleted()).containsExactly("Department", "CostCentre", "LegalEntity");
    }

    @Test
    @DisplayName("another seeder's models are never touched, however adjacent")
    void neverReachesBeyondItsOwn() {
        // The property that survives a service split. A cleaner that "helpfully" also removed related data
        // would delete rows the owning service is about to re-create, from outside that service.
        cleaner.clearModels(TENANT, List.of("Department"));

        assertThat(modelsDeleted())
                .containsExactly("Department")
                .doesNotContain("TenantOptionItem", "Employee", "UserAccount");
    }

    @Test
    @DisplayName("a first provision has nothing to clear and issues no delete")
    void firstProvision_isANoOp() {
        when(modelService.getIds(anyString(), any(Filters.class))).thenReturn(List.of());

        assertThat(cleaner.clearModels(TENANT, List.of("Department"))).isEmpty();
        verify(modelService, never()).deleteByIds(anyString(), anyList());
    }

    // ─── clearing predefined data through its ledger ───

    @Test
    @DisplayName("a ledger row id stored as text still addresses its row")
    void textRowIdsAreConvertedNotDropped() {
        // The ledger stores rowId in a varchar whatever the model's key really is, so every id comes back a
        // String. Reading it as a number looks right and matches nothing: the clear would delete zero rows and
        // report zero, which is indistinguishable from "there was nothing to clear".
        bindings.add(binding(1L, "TenantOptionItem", "873530187038347267"));
        bindings.add(binding(2L, "TenantOptionItem", "873530188493770756"));

        assertThat(cleaner.clearPreData(TENANT)).containsEntry("TenantOptionItem", 2);
        assertThat(idsDeletedFor("TenantOptionItem")).hasSize(2);
    }

    @Test
    @DisplayName("rows are grouped by their own model, children included")
    void groupsLedgerRowsByModel() {
        // Children nested inside a parent's JSON get their own bindings, which is why the ledger — not the seed
        // file list — is the source: no file names TenantOptionItem at top level.
        bindings.add(binding(1L, "TenantOptionSet", "11"));
        bindings.add(binding(2L, "TenantOptionItem", "21"));
        bindings.add(binding(3L, "TenantOptionItem", "22"));

        Map<String, Integer> cleared = cleaner.clearPreData(TENANT);

        assertThat(cleared).containsEntry("TenantOptionSet", 1).containsEntry("TenantOptionItem", 2);
    }

    @Test
    @DisplayName("the bindings go too, or the next load updates rows that are gone")
    void deletesTheLedgerItself() {
        bindings.add(binding(1L, "Role", "11"));

        assertThat(cleaner.clearPreData(TENANT)).containsEntry("SysPreData", 1);
        assertThat(modelsDeleted()).contains("SysPreData");
    }

    @Test
    @DisplayName("no bindings — nothing deleted, not even the ledger")
    void noBindings_isANoOp() {
        assertThat(cleaner.clearPreData(TENANT)).isEmpty();
        verify(modelService, never()).deleteByIds(anyString(), anyList());
    }

    private List<String> modelsDeleted() {
        return deletes.stream().map(Map.Entry::getKey).toList();
    }

    private List<?> idsDeletedFor(String model) {
        return deletes.stream().filter(e -> e.getKey().equals(model))
                .findFirst().map(Map.Entry::getValue).orElse(List.of());
    }

    private static Map<String, Object> binding(Long id, String model, String rowId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("model", model);
        row.put("rowId", rowId);
        return row;
    }
}
