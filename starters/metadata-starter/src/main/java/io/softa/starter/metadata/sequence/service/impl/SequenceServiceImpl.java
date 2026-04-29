package io.softa.starter.metadata.sequence.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.jdbc.JdbcProxy;
import io.softa.framework.orm.jdbc.database.SqlParams;
import io.softa.framework.orm.sequence.SequencePreview;
import io.softa.framework.orm.sequence.SequenceService;
import io.softa.framework.orm.sequence.exception.SequenceCrossTenantException;
import io.softa.framework.orm.sequence.exception.SequenceDisabledException;
import io.softa.framework.orm.sequence.exception.SequenceNotFoundException;
import io.softa.framework.orm.sequence.exception.SequenceTimeoutException;
import io.softa.starter.metadata.sequence.entity.SysSequence;
import io.softa.starter.metadata.sequence.enums.SequenceMode;
import io.softa.starter.metadata.sequence.service.SequenceConfigCache;
import io.softa.starter.metadata.sequence.service.SysSequenceService;
import io.softa.starter.metadata.sequence.service.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Default {@link SequenceService} implementation backed by MySQL's
 * {@code LAST_INSERT_ID(expr)} idiom: a single {@code UPDATE} writes the
 * new {@code current_value} and stashes it in the session register, then
 * {@code SELECT LAST_INSERT_ID()} reads it back on the same connection.
 *
 * <p>The class dispatches by {@link SysSequence#getMode()} into two
 * proxy-routed methods:
 * <ul>
 *   <li>{@link #allocateInOuterTx(String, SysSequence, int)} — joins the
 *       caller's business transaction ({@code MANDATORY}); strict no-gap.</li>
 *   <li>{@link #allocateInNewTx(String, SysSequence, int)} — opens an
 *       independent transaction ({@code REQUIRES_NEW}); business rollback
 *       leaves the counter advanced.</li>
 * </ul>
 * The {@code self} field (Spring proxy injected lazily to break the cycle)
 * is mandatory: a direct {@code this.allocate...} call would bypass the
 * AOP advice and silently disable the propagation annotation.
 *
 * <p>Both single and batch paths share the same dispatcher and SQL skeleton.
 * Batch SQL allocates the entire range under one row-lock acquisition.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SequenceServiceImpl implements SequenceService {

    private static final String MODEL_NAME = "SysSequence";

    private static final String SQL_SINGLE = """
            UPDATE sys_sequence
            SET current_value = LAST_INSERT_ID(
                    CASE
                        WHEN last_reset_key <=> ? THEN current_value + ?
                        ELSE ?
                    END
                ),
                last_reset_key = ?,
                updated_time = CURRENT_TIMESTAMP
            WHERE tenant_id = ? AND code = ? AND status = 'Active'
            """;

    private static final String SQL_BATCH = """
            UPDATE sys_sequence
            SET current_value = LAST_INSERT_ID(
                    CASE
                        WHEN last_reset_key <=> ? THEN current_value + ? * ?
                        ELSE ? + (? - 1) * ?
                    END
                ),
                last_reset_key = ?,
                updated_time = CURRENT_TIMESTAMP
            WHERE tenant_id = ? AND code = ? AND status = 'Active'
            """;

    private static final String SQL_LAST_INSERT_ID = "SELECT LAST_INSERT_ID()";

    private final JdbcProxy jdbcProxy;
    private final SequenceConfigCache configCache;
    private final SysSequenceService sysSequenceService;
    private final TemplateRenderer templateRenderer;

    /** Self-proxy for transactional dispatch; must be lazy to break the cycle. */
    @Autowired
    @Lazy
    private SequenceServiceImpl self;

    @Override
    public String next(String code) {
        return dispatch(code, 1).get(0);
    }

    @Override
    public List<String> nextBatch(String code, int count) {
        Assert.isTrue(count > 0, "count must be positive");
        return dispatch(code, count);
    }

    @Override
    public SequencePreview peek(String code) {
        rejectCrossTenant(code);
        SysSequence cfg = configCache.load(code);
        LocalDateTime now = LocalDateTime.now();
        String currentKey = cfg.getResetCadence().computeKey(now);
        long preview;
        if (currentKey.equals(cfg.getLastResetKey())) {
            preview = (cfg.getCurrentValue() == null ? 0L : cfg.getCurrentValue()) + cfg.getIncrementStep();
        } else {
            preview = cfg.getStartValue();
        }
        String rendered = templateRenderer.render(cfg.getTemplate(), preview, now, code);
        return new SequencePreview(code, rendered, preview, "Preview only, not reserved");
    }

    /** Mode dispatcher; goes through {@link #self} so the propagation annotations apply. */
    private List<String> dispatch(String code, int count) {
        rejectCrossTenant(code);
        SysSequence cfg = configCache.load(code);
        return SequenceMode.ALLOW_GAP == cfg.getMode()
                ? self.allocateInNewTx(code, cfg, count)
                : self.allocateInOuterTx(code, cfg, count);
    }

    /**
     * NO_GAP path. Joins the caller's transaction; row lock is held until
     * the outer business transaction commits or rolls back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> allocateInOuterTx(String code, SysSequence cfg, int count) {
        return doAllocate(code, cfg, count);
    }

    /**
     * ALLOW_GAP path. Suspends the outer transaction (if any), opens a fresh
     * physical transaction on a separate connection, commits it, then
     * resumes. Business rollback after this point cannot retract the counter.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> allocateInNewTx(String code, SysSequence cfg, int count) {
        return doAllocate(code, cfg, count);
    }

    private List<String> doAllocate(String code, SysSequence cfg, int count) {
        Long tenantId = ContextHolder.getContext().getTenantId();
        long step = cfg.getIncrementStep();
        long startValue = cfg.getStartValue();
        LocalDateTime now = LocalDateTime.now();
        String currentKey = cfg.getResetCadence().computeKey(now);

        SqlParams sqlParams = buildAllocateSql(currentKey, step, startValue, count, tenantId, code);

        int rows;
        try {
            rows = jdbcProxy.update(MODEL_NAME, sqlParams);
        } catch (CannotAcquireLockException e) {
            throw new SequenceTimeoutException(code, e);
        }
        if (rows == 0) {
            // No row updated — could be (a) row never existed, or (b) row exists but
            // status was flipped to Disabled (possibly after the cached config was loaded).
            // Cold path: bypass cache and consult DB directly.
            throw disabledOrMissing(code);
        }

        Long endValue = (Long) jdbcProxy.queryForObject(
                MODEL_NAME, new SqlParams(SQL_LAST_INSERT_ID), Long.class);
        if (endValue == null) {
            throw new SequenceNotFoundException(code);
        }

        long start = endValue - (long) (count - 1) * step;
        List<String> result = new ArrayList<>(count);
        String template = cfg.getTemplate();
        for (int i = 0; i < count; i++) {
            long n = start + (long) i * step;
            result.add(templateRenderer.render(template, n, now, code));
        }
        return result;
    }

    private SqlParams buildAllocateSql(String currentKey, long step, long startValue,
                                       int count, Long tenantId, String code) {
        SqlParams sqlParams;
        if (count == 1) {
            sqlParams = new SqlParams(SQL_SINGLE);
            sqlParams.addArgValue(currentKey);
            sqlParams.addArgValue(step);
            sqlParams.addArgValue(startValue);
            sqlParams.addArgValue(currentKey);
            sqlParams.addArgValue(tenantId);
            sqlParams.addArgValue(code);
        } else {
            sqlParams = new SqlParams(SQL_BATCH);
            sqlParams.addArgValue(currentKey);
            sqlParams.addArgValue(step);
            sqlParams.addArgValue(count);
            sqlParams.addArgValue(startValue);
            sqlParams.addArgValue(count);
            sqlParams.addArgValue(step);
            sqlParams.addArgValue(currentKey);
            sqlParams.addArgValue(tenantId);
            sqlParams.addArgValue(code);
        }
        return sqlParams;
    }

    private RuntimeException disabledOrMissing(String code) {
        // Cold path: row exists but status != 'Active', or row does not exist at all.
        // searchOne goes through EntityService (auto-filters by tenant for multiTenant=true models).
        // No status filter here, so we can tell missing apart from disabled.
        FlexQuery q = new FlexQuery(new Filters().eq(SysSequence::getCode, code));
        boolean exists = sysSequenceService.searchOne(q).isPresent();
        return exists ? new SequenceDisabledException(code) : new SequenceNotFoundException(code);
    }

    private void rejectCrossTenant(String code) {
        if (ContextHolder.getContext().isCrossTenant()) {
            throw new SequenceCrossTenantException(code);
        }
    }
}
