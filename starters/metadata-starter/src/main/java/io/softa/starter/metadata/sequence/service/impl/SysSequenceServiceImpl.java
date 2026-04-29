package io.softa.starter.metadata.sequence.service.impl;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.metadata.sequence.entity.SysSequence;
import io.softa.starter.metadata.sequence.service.SysSequenceService;
import org.springframework.stereotype.Service;

/**
 * Plain {@link EntityServiceImpl} for {@link SysSequence}. Cache eviction
 * and cross-instance broadcast on mutation are handled by
 * {@code SysSequenceChangeListener}, which subscribes to the framework's
 * {@code TransactionEvent} (transactional after-commit) and inspects
 * {@code ChangeLogHolder} for {@code SysSequence} rows. That listener-based
 * approach intercepts <strong>all</strong> mutation paths — generic
 * {@code ModelController} REST, {@code SysPreDataService.loadPreTenantData},
 * and direct EntityService calls — without needing per-entrypoint wrappers
 * here.
 */
@Service
public class SysSequenceServiceImpl extends EntityServiceImpl<SysSequence, Long>
        implements SysSequenceService {
}
