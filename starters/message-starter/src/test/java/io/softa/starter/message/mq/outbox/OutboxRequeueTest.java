package io.softa.starter.message.mq.outbox;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.softa.framework.base.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Manual requeue contract: only DEAD entries are accepted, and the reopened row
 * gets a FRESH publish budget (attempts reset to 0, due immediately) — DEAD means
 * the previous budget was spent against a broken broker.
 */
class OutboxRequeueTest {

    private OutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new OutboxServiceImpl());
    }

    private OutboxEntry entry(OutboxStatus status) {
        OutboxEntry entry = new OutboxEntry();
        entry.setId(11L);
        entry.setStatus(status);
        entry.setVersion(4L);
        entry.setAttempts(10);
        doReturn(Optional.of(entry)).when(service).getById(11L);
        return entry;
    }

    @Test
    void deadEntry_isRequeuedWithAFreshPublishBudget() {
        entry(OutboxStatus.DEAD);
        doReturn(true).when(service).markNew(eq(11L), eq(4L), eq(0), anyString(),
                any(LocalDateTime.class));

        assertTrue(service.requeue(11L));

        verify(service).markNew(eq(11L), eq(4L), eq(0), anyString(), any(LocalDateTime.class));
    }

    @Test
    void nonDeadEntry_isRejected() {
        entry(OutboxStatus.PUBLISHED);

        assertThrows(BusinessException.class, () -> service.requeue(11L));
        verify(service, never()).markNew(anyLong(), anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void concurrentVersionMiss_surfacesAsBusinessError() {
        entry(OutboxStatus.DEAD);
        doReturn(false).when(service).markNew(eq(11L), eq(4L), eq(0), anyString(),
                any(LocalDateTime.class));

        assertThrows(BusinessException.class, () -> service.requeue(11L));
    }
}
