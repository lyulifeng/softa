package io.softa.starter.message.service.internal;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MessageScope;
import io.softa.starter.message.config.MessageProperties;
import io.softa.starter.message.mail.dto.SendMailDTO;
import io.softa.starter.message.mail.entity.MailSendRecord;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.enums.BodyMode;
import io.softa.starter.message.mail.service.MailSendRecordService;
import io.softa.starter.message.mail.service.MailTemplateService;
import io.softa.starter.message.mail.support.MailServerDispatcher;
import io.softa.starter.message.mq.TopicRoute;
import io.softa.starter.message.shared.MonthlyQuotaGuard;
import io.softa.starter.message.mq.outbox.OutboxRecordWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Acceptance-side contract for one mail message. */
class MailMessageHandlerTest {

    private MailServerDispatcher dispatcher;
    private MailSendRecordService recordService;
    private MailTemplateService templateService;
    private OutboxRecordWriter outboxRecordWriter;
    private MessageProperties messageProperties;
    private MonthlyQuotaGuard quotaGuard;
    private MailMessageHandler handler;

    @BeforeEach
    void setUp() {
        dispatcher = mock(MailServerDispatcher.class);
        recordService = mock(MailSendRecordService.class);
        templateService = mock(MailTemplateService.class);
        outboxRecordWriter = mock(OutboxRecordWriter.class);
        messageProperties = new MessageProperties();
        quotaGuard = mock(MonthlyQuotaGuard.class);
        handler = new MailMessageHandler(dispatcher, recordService, templateService, outboxRecordWriter,
                messageProperties, quotaGuard);

        when(outboxRecordWriter.persistAndEnqueue(any(), eq("MailSendRecord"), eq(TopicRoute.MAIL_SEND)))
                .thenAnswer(invocation -> ((Supplier<Long>) invocation.getArgument(0)).get());
        when(recordService.createOne(any(MailSendRecord.class))).thenReturn(42L);
        when(dispatcher.resolveSend(MessageScope.TENANT)).thenReturn(config(10L));
    }

    @Test
    void bodyOverConfiguredLimitIsRejected() {
        messageProperties.getMail().setMaxBodyChars(10);
        SendMailDTO dto = new SendMailDTO();
        dto.setTo(List.of("alice@example.com"));
        dto.setSubject("Hi");
        dto.setTextBody("x".repeat(11));

        BusinessException ex = assertThrows(BusinessException.class, () -> handler.send(dto));
        assertTrue(ex.getMessage().contains("maximum length"));
        verifyNoInteractions(outboxRecordWriter);
    }

    @Test
    void sendPersistsPendingRecordAndEnqueuesOutbox() {
        SendMailDTO dto = new SendMailDTO();
        dto.setTo(List.of("alice@example.com"));
        dto.setSubject("Hi");
        dto.setTextBody("Body");

        Long id = handler.send(dto);

        assertEquals(42L, id);
        MailSendRecord record = capturedRecord();
        assertEquals("Hi", record.getSubject());
        assertEquals("Body", record.getBodyText());
    }

    @Test
    void templateByIdRequest_addressesExactRow_ignoringResolution() {
        MailTemplate template = htmlTemplate("WELCOME");
        when(templateService.getRequiredById(7L)).thenReturn(template);
        when(templateService.renderSubject(eq(template), any())).thenReturn("Welcome, Alice!");
        when(templateService.renderBodyHtml(eq(template), any())).thenReturn("<p>Hello Alice</p>");

        SendMailDTO dto = new SendMailDTO();
        dto.setTo(List.of("alice@example.com"));
        dto.setTemplateId(7L);
        dto.setTemplateCode("IGNORED");
        dto.setTemplateVariables(Map.of("name", "Alice"));

        handler.send(dto);
        verify(templateService, never()).resolve(any(), any());
    }

    @Test
    void templateRequestRendersContent() {
        MailTemplate template = htmlTemplate("WELCOME");
        when(templateService.resolve("WELCOME", MessageScope.TENANT)).thenReturn(template);
        when(templateService.renderSubject(eq(template), any())).thenReturn("Welcome, Alice!");
        when(templateService.renderBodyHtml(eq(template), any())).thenReturn("<p>Hello Alice</p>");

        SendMailDTO dto = new SendMailDTO();
        dto.setTo(List.of("alice@example.com"));
        dto.setTemplateCode("WELCOME");
        dto.setTemplateVariables(Map.of("name", "Alice"));

        handler.send(dto);

        MailSendRecord record = capturedRecord();
        assertEquals("Welcome, Alice!", record.getSubject());
        assertEquals("<p>Hello Alice</p>", record.getBodyHtml());
        assertNull(dto.getSubject());
        assertNull(dto.getHtmlBody());
        assertNull(dto.getTextBody());
        assertNull(dto.getBodyMode());
        verify(templateService).resolve("WELCOME", MessageScope.TENANT);
    }

    @Test
    void templatePreferredServerIsAppliedBeforeConfigResolution() {
        MailTemplate template = htmlTemplate("MARKETING");
        template.setPreferredServerConfigId(99L);
        when(templateService.resolve("MARKETING", MessageScope.TENANT)).thenReturn(template);
        when(templateService.renderSubject(eq(template), any())).thenReturn("S");
        when(templateService.renderBodyHtml(eq(template), any())).thenReturn("<p>B</p>");
        when(dispatcher.resolveSendById(99L)).thenReturn(config(99L));

        SendMailDTO dto = new SendMailDTO();
        dto.setTo(List.of("alice@example.com"));
        dto.setTemplateCode("MARKETING");

        handler.send(dto);

        verify(dispatcher).resolveSendById(99L);
        verify(dispatcher, never()).resolveSend(any());
        assertEquals(99L, capturedRecord().getServerConfigId());
        assertNull(dto.getServerConfigId());
    }

    @Test
    void missingRecipientsIsRejectedBeforePersisting() {
        SendMailDTO dto = new SendMailDTO();
        dto.setTextBody("Body");

        assertThrows(BusinessException.class, () -> handler.send(dto));
        verifyNoInteractions(recordService, outboxRecordWriter);
    }

    @Test
    void missingContentIsRejectedBeforePersisting() {
        SendMailDTO dto = new SendMailDTO();
        dto.setTo(List.of("alice@example.com"));

        assertThrows(BusinessException.class, () -> handler.send(dto));
        verifyNoInteractions(recordService, outboxRecordWriter);
    }

    @Test
    void platformScope_governsTemplateAndServerResolution() {
        MailTemplate template = htmlTemplate("INVOICE_ISSUED");
        when(templateService.resolve("INVOICE_ISSUED", MessageScope.PLATFORM)).thenReturn(template);
        when(templateService.renderSubject(eq(template), any())).thenReturn("Invoice");
        when(templateService.renderBodyHtml(eq(template), any())).thenReturn("<p>Invoice</p>");
        when(dispatcher.resolveSend(MessageScope.PLATFORM)).thenReturn(config(10L));

        SendMailDTO dto = new SendMailDTO();
        dto.setTo(List.of("alice@example.com"));
        dto.setTemplateCode("INVOICE_ISSUED");
        dto.setScope(MessageScope.PLATFORM);

        handler.send(dto);

        // One declared scope governs BOTH axes: template tier and server tier.
        verify(templateService).resolve("INVOICE_ISSUED", MessageScope.PLATFORM);
        verify(dispatcher).resolveSend(MessageScope.PLATFORM);
        verify(dispatcher, never()).resolveSend(MessageScope.TENANT);
    }

    private MailSendServerConfig config(long id) {
        MailSendServerConfig config = new MailSendServerConfig();
        config.setId(id);
        config.setFromAddress("noreply@example.com");
        return config;
    }

    private MailTemplate htmlTemplate(String code) {
        MailTemplate template = new MailTemplate();
        template.setCode(code);
        template.setBodyMode(BodyMode.HTML);
        return template;
    }

    private MailSendRecord capturedRecord() {
        ArgumentCaptor<MailSendRecord> captor = ArgumentCaptor.forClass(MailSendRecord.class);
        verify(recordService).createOne(captor.capture());
        return captor.getValue();
    }
}
