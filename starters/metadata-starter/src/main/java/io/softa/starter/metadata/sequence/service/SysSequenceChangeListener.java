package io.softa.starter.metadata.sequence.service;

import java.util.List;
import java.util.Map;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.changelog.ChangeLogHolder;
import io.softa.framework.orm.changelog.event.TransactionEvent;
import io.softa.framework.orm.changelog.message.dto.ChangeLog;
import io.softa.framework.orm.enums.AccessType;
import io.softa.starter.metadata.sequence.entity.SysSequence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to {@code SysSequence} mutations by evicting the local
 * {@link SequenceConfigCache} entry and broadcasting the invalidation to
 * peer JVMs. Hooks into the framework's {@code TransactionEvent} after the
 * surrounding transaction commits — so the cache only drops once the new
 * config is actually visible on disk.
 *
 * <p>Catches every mutation path uniformly: generic {@code ModelController}
 * REST endpoints, {@code SysPreDataService.loadPreTenantData} bootstraps,
 * direct {@code EntityService} calls and admin scripts all funnel through
 * {@code JdbcServiceImpl}'s changelog publication.
 *
 * <p>For each relevant {@link ChangeLog} we read the row's {@code code}
 * value and the affected tenant; both are required to compute the cache
 * key. If either is missing (stale event, custom path) we silently skip
 * — better to leave a 5-min stale entry than to evict the wrong key.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SysSequenceChangeListener {

    private static final String MODEL_NAME = "SysSequence";
    private static final String FIELD_CODE = "code";

    private final SequenceConfigCache configCache;
    private final SeqInvalidateBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(TransactionEvent event) {
        List<ChangeLog> changeLogs = ChangeLogHolder.get();
        if (changeLogs == null || changeLogs.isEmpty()) {
            return;
        }
        for (ChangeLog log : changeLogs) {
            if (!MODEL_NAME.equals(log.getModel())) {
                continue;
            }
            String code = extractCode(log);
            Long tenantId = log.getTenantId();
            if (code == null || tenantId == null) {
                continue;
            }
            try {
                configCache.evictExplicit(tenantId, code);
            } catch (Exception e) {
                SysSequenceChangeListener.log.warn("Failed to evict sequence cache tenant={} code={}: {}",
                        tenantId, code, e.getMessage());
            }
            // Broadcast under the changelog's tenant so peers can rebuild the same key.
            try {
                Context scoped = ContextHolder.getContext().copy();
                scoped.setTenantId(tenantId);
                ContextHolder.runWith(scoped, () -> broadcaster.publish(code));
            } catch (Exception e) {
                SysSequenceChangeListener.log.warn("Failed to broadcast sequence invalidation tenant={} code={}: {}",
                        tenantId, code, e.getMessage());
            }
        }
    }

    /**
     * Pull the {@code code} field out of the changelog snapshot.
     * For CREATE the value lives in {@code dataAfterChange}; for UPDATE/DELETE
     * the original (pre-change) row carries the most reliable identifier.
     */
    private String extractCode(ChangeLog log) {
        AccessType type = log.getAccessType();
        Map<String, Object> source = type == AccessType.CREATE
                ? log.getDataAfterChange()
                : log.getDataBeforeChange();
        if (source == null) {
            // UPDATE may put the new code into dataAfterChange when the field changed.
            source = log.getDataAfterChange();
        }
        Object value = source == null ? null : source.get(FIELD_CODE);
        return value instanceof String s ? s : null;
    }
}
