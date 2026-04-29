package io.softa.starter.metadata.sequence.service;

import java.util.List;
import java.util.Map;

import io.softa.framework.orm.jdbc.JdbcProxy;
import io.softa.framework.orm.jdbc.database.SqlParams;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaFieldInternalAccess;
import io.softa.framework.orm.meta.ModelManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Scans {@code sys_sequence} once at application start and writes
 * {@code MetaField.autoSequence = true} on every field whose code matches
 * the {@code "<modelName>.<fieldName>"} convention.
 *
 * <p>Runs at {@link ApplicationReadyEvent} with low precedence so any
 * upstream tenant / data bootstrapping has already finished.
 *
 * <p><strong>v1 design</strong>: registry is built exactly once at startup
 * and is effectively read-only thereafter. Adding new sequence codes goes
 * through the normal release process — operators run
 * {@code loadPreTenantData} for the new JSON files <em>before</em> the new
 * application instances start, so the next {@code initialize()} sees the
 * full set. There is no runtime refresh endpoint and no cluster-rebuild
 * broadcast; the only runtime invalidation is config-cache eviction on
 * mutating updates of existing rows.
 *
 * <p><strong>Cross-tenant scan</strong>: codes are structurally identical
 * across tenants in v1 (sequences are bound to business models, not to
 * tenant-specific config). We therefore go straight at the table with
 * {@code SELECT DISTINCT code} via {@link JdbcProxy} — bypassing the
 * EntityService tenant filter that would otherwise auto-add
 * {@code WHERE tenant_id=?} from the (empty) startup context. DISTINCT
 * folds duplicate rows, so the cost is O(unique codes) regardless of how
 * many tenants are loaded.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class SequenceFieldRegistryInitializer {

    private static final String MODEL_NAME = "SysSequence";
    private static final String FIELD_CODE = "code";
    private static final String SCAN_SQL =
            "SELECT DISTINCT code FROM sys_sequence WHERE status = 'Active'";

    private final JdbcProxy jdbcProxy;

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        try {
            initialize();
        } catch (Exception e) {
            log.error("Sequence field registry initialization failed", e);
        }
    }

    /**
     * Scan {@code sys_sequence} and write {@code MetaField.autoSequence}
     * across all known models. Idempotent. Package-private because v1 only
     * invokes this from {@link #onAppReady()} — runtime callers go through
     * the release process to add new codes; tests inside the same package
     * may call this directly for assertions.
     */
    void initialize() {
        List<Map<String, Object>> rows = jdbcProxy.queryForList(MODEL_NAME, new SqlParams(SCAN_SQL));
        int bound = 0;
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            Object raw = row.get(FIELD_CODE);
            if (!(raw instanceof String code) || code.isBlank()) {
                skipped++;
                continue;
            }
            int dot = code.indexOf('.');
            if (dot <= 0 || dot >= code.length() - 1) {
                log.warn("Skip non-convention sequence code [{}]; auto-fill not enabled", code);
                skipped++;
                continue;
            }
            String model = code.substring(0, dot);
            String field = code.substring(dot + 1);
            MetaField mf = ModelManager.getModelFieldOrNull(model, field);
            if (mf == null) {
                log.warn("Sequence code [{}] points to unknown field {}::{}", code, model, field);
                skipped++;
                continue;
            }
            MetaFieldInternalAccess.setAutoSequence(mf, true);
            bound++;
        }
        log.info("Sequence field registry initialized: bound={}, skipped={}", bound, skipped);
    }
}
