package io.softa.starter.message.mq.outbox;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;

/**
 * REST controller for transactional outbox diagnostics (read-heavy admin UI)
 * plus the manual {@code requeue} operation for DEAD entries.
 */
@Tag(name = "OutboxEntry")
@RestController
@RequestMapping("/OutboxEntry")
public class OutboxEntryController
        extends EntityController<OutboxService, OutboxEntry, Long> {

    /**
     * Requeue a DEAD entry (publish budget exhausted against a broken broker):
     * back to NEW, attempts reset, due immediately. Other statuses are rejected.
     */
    @Operation(summary = "Requeue one DEAD outbox entry for publishing")
    @PostMapping("/requeue")
    public ApiResponse<Boolean> requeue(@RequestParam Long id) {
        return ApiResponse.success(service.requeue(id));
    }
}
