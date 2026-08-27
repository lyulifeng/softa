package io.softa.starter.message.mail.controller;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
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
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.service.MailTemplateService;

/**
 * REST controller for email template management.
 * <p>
 * Provides standard CRUD and search endpoints inherited from the generic
 * model routes. The template tiers are fully separate namespaces: a tenant
 * session manages the tenant's own rows (copies arrive at provisioning), a
 * platform session manages the platform tier — this controller serves both,
 * scoped by the caller's context.
 * <p>
 * The single-row write endpoints shadow the generic {@code /{modelName}/...}
 * routes (a literal path is more specific) to enforce the scope rules: a
 * template may only pin a {@code preferredServerConfigId} owned by its own
 * scope, and a write addressed at a platform row from a tenant scope fails
 * with an explanation instead of the ORM's silent no-op.
 */
@Tag(name = "MailTemplate")
@RestController
@RequestMapping("/MailTemplate")
public class MailTemplateController
        extends EntityController<MailTemplateService, MailTemplate, Long> {

    private static final String MODEL_NAME = "MailTemplate";

    @Autowired
    private ModelService<Long> modelService;

    @Operation(summary = "Create a mail template — the preferred send server must be in the template's scope")
    @PostMapping("/createOne")
    @DataMask
    @Transactional
    public ApiResponse<Long> createOne(@RequestBody Map<String, Object> row) {
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.createOne(MODEL_NAME, row));
    }

    @Operation(summary = "Create a mail template and fetch — the preferred send server must be in the template's scope")
    @PostMapping("/createOneAndFetch")
    @DataMask
    @Transactional
    public ApiResponse<Map<String, Object>> createOneAndFetch(@RequestBody Map<String, Object> row) {
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.createOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE));
    }

    @Operation(summary = "Update a mail template — platform rows are rejected for tenant callers; "
            + "the preferred send server must be in the template's scope")
    @PostMapping("/updateOne")
    @DataMask
    @Transactional
    public ApiResponse<Boolean> updateOne(@RequestBody Map<String, Object> row) {
        service.assertWritableInCurrentScope(RowValues.id(row));
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.updateOne(MODEL_NAME, row));
    }

    @Operation(summary = "Update a mail template and fetch — platform rows are rejected for tenant callers; "
            + "the preferred send server must be in the template's scope")
    @PostMapping("/updateOneAndFetch")
    @DataMask
    @Transactional
    public ApiResponse<Map<String, Object>> updateOneAndFetch(@RequestBody Map<String, Object> row) {
        service.assertWritableInCurrentScope(RowValues.id(row));
        service.validatePreferredServerScope(row);
        return ApiResponse.success(modelService.updateOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE));
    }

    @Operation(summary = "Delete a mail template — platform rows are rejected for tenant callers")
    @PostMapping("/deleteById")
    public ApiResponse<Boolean> deleteById(@RequestParam Long id) {
        // service.deleteById turns the cross-scope silent no-op into an
        // explanatory error (assertWritableInCurrentScope).
        return ApiResponse.success(service.deleteById(id));
    }
}
