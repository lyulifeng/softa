package io.softa.starter.message.mail.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.starter.message.mail.entity.MailSendRecord;
import io.softa.starter.message.mail.enums.MailSendStatus;
import io.softa.starter.message.mq.TopicRoute;
import io.softa.starter.message.mq.outbox.OutboxRecordWriter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Status-gate contract for the manual retry: PENDING / RETRY / FAILED / DEAD_LETTER
 * are requeued through the atomic CAS-plus-outbox primitive; SENT and in-flight
 * SENDING are rejected before anything is written.
 */
class MailSendRecordRetryTest {

    private MailSendRecordServiceImpl service;
    private OutboxRecordWriter outboxRecordWriter;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new MailSendRecordServiceImpl());
        outboxRecordWriter = mock(OutboxRecordWriter.class);
        ReflectionTestUtils.setField(service, "outboxRecordWriter", outboxRecordWriter);
    }

    private MailSendRecord record(MailSendStatus status) {
        MailSendRecord record = new MailSendRecord();
        record.setId(7L);
        record.setStatus(status);
        record.setVersion(3L);
        doReturn(Optional.of(record)).when(service).getById(7L);
        return record;
    }

    @Test
    void pendingRecord_isRequeuedThroughTheAtomicPrimitive() {
        record(MailSendStatus.PENDING);
        when(outboxRecordWriter.transitionAndEnqueueAt(any(BooleanSupplier.class), eq(7L),
                eq("MailSendRecord"), eq(TopicRoute.MAIL_SEND), any(LocalDateTime.class)))
                .thenReturn(true);

        assertTrue(service.retry(7L));
    }

    @Test
    void deadLetterRecord_isRetryable() {
        record(MailSendStatus.DEAD_LETTER);
        when(outboxRecordWriter.transitionAndEnqueueAt(any(BooleanSupplier.class), eq(7L),
                eq("MailSendRecord"), eq(TopicRoute.MAIL_SEND), any(LocalDateTime.class)))
                .thenReturn(true);

        assertTrue(service.retry(7L));
    }

    @Test
    void sentRecord_isRejectedWithoutTouchingTheOutbox() {
        record(MailSendStatus.SENT);

        assertThrows(BusinessException.class, () -> service.retry(7L));
        verify(outboxRecordWriter, never()).transitionAndEnqueueAt(any(), anyLong(), any(), any(), any());
    }

    @Test
    void inFlightSendingRecord_isRejected() {
        record(MailSendStatus.SENDING);

        assertThrows(BusinessException.class, () -> service.retry(7L));
        verify(outboxRecordWriter, never()).transitionAndEnqueueAt(any(), anyLong(), any(), any(), any());
    }

    @Test
    void concurrentVersionMiss_surfacesAsBusinessError() {
        record(MailSendStatus.RETRY);
        when(outboxRecordWriter.transitionAndEnqueueAt(any(BooleanSupplier.class), eq(7L),
                eq("MailSendRecord"), eq(TopicRoute.MAIL_SEND), any(LocalDateTime.class)))
                .thenReturn(false);

        assertThrows(BusinessException.class, () -> service.retry(7L));
    }
}
