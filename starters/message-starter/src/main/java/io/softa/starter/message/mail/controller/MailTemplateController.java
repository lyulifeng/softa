package io.softa.starter.message.mail.controller;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.orm.annotation.DataMask;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.message.mail.dto.MailTemplateEffectiveDTO;
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.service.MailTemplateService;

/**
 * REST controller for email template management.
 * <p>
 * Provides standard CRUD and search endpoints inherited from the generic
 * model routes. Templates support platform/tenant-level scoping.
 * <p>
 * The single-row write endpoints shadow the generic {@code /{modelName}/...}
 * routes (a literal path is more specific) to enforce the overlay rules:
 * a template may only pin a {@code preferredServerConfigId} its scope may
 * select, a tenant create must not shadow a locked platform code, and a write
 * addressed at a platform row from a tenant scope fails with an explanation
 * instead of the ORM's silent no-op.
 * <p>
 * The overlay management surface lives here too: {@code /effectiveList} (the
 * tenant's own templates plus the inherited platform tier, one row per code)
 * and {@code /customize} (copy-on-write of a platform template into the
 * caller's tenant — the override flow never hand-types a code).
 */
@Tag(name = "MailTemplate")
@RestController
@RequestMapping("/MailTemplate")
public class MailTemplateController
        extends EntityController<MailTemplateService, MailTemplate, Long> {

    private static final String MODEL_NAME = "MailTemplate";

    @Autowired
    private ModelService<Long> modelService;

    @Operation(summary = "Create a mail template — the preferred send server must be selectable in "
            + "the template's scope, and the code must not shadow a locked platform template")
    @PostMapping("/createOne")
    @DataMask
    @Transactional
    public ApiResponse<Long> createOne(@RequestBody Map<String, Object> row) {
        service.validateCodeOverride(row);
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.createOne(MODEL_NAME, row));
    }

    @Operation(summary = "Create a mail template and fetch — the preferred send server must be selectable in "
            + "the template's scope, and the code must not shadow a locked platform template")
    @PostMapping("/createOneAndFetch")
    @DataMask
    @Transactional
    public ApiResponse<Map<String, Object>> createOneAndFetch(@RequestBody Map<String, Object> row) {
        service.validateCodeOverride(row);
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.createOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE));
    }

    @Operation(summary = "Update a mail template — platform rows are rejected for tenant callers; "
            + "the preferred send server must be selectable in the template's scope")
    @PostMapping("/updateOne")
    @DataMask
    @Transactional
    public ApiResponse<Boolean> updateOne(@RequestBody Map<String, Object> row) {
        service.assertWritableInCurrentScope(RowValues.id(row));
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.updateOne(MODEL_NAME, row));
    }

    @Operation(summary = "Update a mail template and fetch — platform rows are rejected for tenant callers; "
            + "the preferred send server must be selectable in the template's scope")
    @PostMapping("/updateOneAndFetch")
    @DataMask
    @Transactional
    public ApiResponse<Map<String, Object>> updateOneAndFetch(@RequestBody Map<String, Object> row) {
        service.assertWritableInCurrentScope(RowValues.id(row));
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.updateOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE));
    }

    @Operation(summary = "Delete a mail template — deleting a tenant copy reverts to the inherited "
            + "platform template; platform rows are rejected for tenant callers")
    @PostMapping("/deleteById")
    public ApiResponse<Boolean> deleteById(@RequestParam Long id) {
        // service.deleteById turns the cross-scope silent no-op into an
        // explanatory error (assertWritableInCurrentScope).
        return ApiResponse.success(service.deleteById(id));
    }

    @Operation(summary = "The effective template list — the caller's own templates plus the inherited "
            + "platform tier, one row per code, each tagged INHERITED / CUSTOMIZED / OWN")
    @GetMapping("/effectiveList")
    public ApiResponse<List<MailTemplateEffectiveDTO>> effectiveList() {
        return ApiResponse.success(service.listEffective());
    }

    @Operation(summary = "Customize an inherited platform template — copies it into the caller's "
            + "tenant under the same code (copy-on-write); the copy shadows the platform template "
            + "until it is deleted",
            description = "Rejected for locked platform templates (overridable = false), for codes "
                    + "already customized in this tenant, and outside multi-tenant deployments. "
                    + "Returns the id of the new tenant copy — open the editor with it.")
    @PostMapping("/customize")
    public ApiResponse<Long> customize(@RequestParam Long id) {
        return ApiResponse.success(service.customize(id));
    }
}
