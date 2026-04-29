package io.softa.starter.metadata.sequence.controller;

import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.sequence.SequencePreview;
import io.softa.framework.orm.sequence.SequenceService;
import io.softa.framework.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sequence-specific admin actions that do not fit the generic CRUD shape.
 * v1 only exposes {@code peek} (read-only preview) — counter resets are
 * intentionally not exposed via REST in this version: they would let
 * platform admins rewrite tenant counters without an audit trail. Add a
 * separate, authenticated maintenance API once that workflow is needed.
 *
 * <p>{@code next} / {@code nextBatch} are <strong>never</strong> exposed
 * over HTTP — they must only be invoked by backend code inside a business
 * transaction (so number consumption stays bounded by ACID semantics).
 */
@Tag(name = "Sequence")
@RestController
@RequestMapping("/Sequence")
@RequiredArgsConstructor
public class SequenceController {

    private final SequenceService sequenceService;

    @Operation(summary = "peek", description = """
            Preview the next sequence value for the given code. Read-only,
            no number is consumed; the actual value handed to next() may
            differ if another caller allocates first.
            """)
    @PostMapping("/peek")
    public ApiResponse<SequencePreview> peek(@RequestParam("code") String code) {
        Assert.notBlank(code, "code must not be blank");
        return ApiResponse.success(sequenceService.peek(code));
    }
}
