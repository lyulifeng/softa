package io.softa.starter.message.mail.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.message.mail.entity.MailSendRecord;
import io.softa.starter.message.mail.service.MailSendRecordService;

/**
 * REST controller for outgoing mail records: a read-only audit log plus the manual
 * {@code retry} operation. Records are created automatically by MessageService and
 * must not be created via API.
 */
@Tag(name = "MailSendRecord")
@RestController
@RequestMapping("/MailSendRecord")
public class MailSendRecordController
        extends EntityController<MailSendRecordService, MailSendRecord, Long> {

    /**
     * Manually requeue a stalled or failed record for delivery. Accepts
     * PENDING / RETRY / FAILED / DEAD_LETTER; rejects SENT and in-flight SENDING.
     * Safe to call repeatedly — the delivery claim is CAS-guarded, so a duplicate
     * requeue no-ops at the consumer.
     */
    @Operation(summary = "Manually requeue one mail record for delivery")
    @PostMapping("/retry")
    public ApiResponse<Boolean> retry(@RequestParam Long id) {
        return ApiResponse.success(service.retry(id));
    }
}
