package io.softa.starter.message.mail.controller;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.softa.framework.orm.annotation.DataMask;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.message.mail.dto.ConnectivityTestResultDTO;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.service.MailSendServerConfigService;

/**
 * REST controller for outgoing mail server configuration (SMTP/SMTPS).
 * <p>
 * The single-row write endpoints shadow the generic
 * {@code /{modelName}/...} routes (a literal path is more specific), so the
 * standard admin UI writes flow through here — after the row is saved with
 * {@code isDefault = true}, every other default in the same tenant scope is
 * demoted, keeping at most one default per scope. Batch and copy endpoints
 * are not shadowed; rows written through them fall back to the dispatcher's
 * {@code sequence} tie-break.
 */
@Tag(name = "MailSendServerConfig")
@RestController
@RequestMapping("/MailSendServerConfig")
public class MailSendServerConfigController
        extends EntityController<MailSendServerConfigService, MailSendServerConfig, Long> {

    private static final String MODEL_NAME = "MailSendServerConfig";

    @Autowired
    private ModelService<Long> modelService;

    @Operation(summary = "Create a send server config — marking it default demotes other defaults")
    @PostMapping("/createOne")
    @DataMask
    @Transactional
    public ApiResponse<Long> createOne(@RequestBody Map<String, Object> row) {
        Long id = modelService.createOne(MODEL_NAME, row);
        if (RowValues.isTrue(row, "isDefault")) {
            service.demoteOtherDefaults(id);
        }
        return ApiResponse.success(id);
    }

    @Operation(summary = "Create a send server config and fetch — marking it default demotes other defaults")
    @PostMapping("/createOneAndFetch")
    @DataMask
    @Transactional
    public ApiResponse<Map<String, Object>> createOneAndFetch(@RequestBody Map<String, Object> row) {
        Map<String, Object> created = modelService.createOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE);
        if (RowValues.isTrue(row, "isDefault")) {
            service.demoteOtherDefaults(RowValues.id(created));
        }
        return ApiResponse.success(created);
    }

    @Operation(summary = "Update a send server config — platform rows are rejected for tenant callers; "
            + "marking it default demotes other defaults")
    @PostMapping("/updateOne")
    @DataMask
    @Transactional
    public ApiResponse<Boolean> updateOne(@RequestBody Map<String, Object> row) {
        service.assertWritableInCurrentScope(RowValues.id(row));
        boolean updated = modelService.updateOne(MODEL_NAME, row);
        if (RowValues.isTrue(row, "isDefault")) {
            service.demoteOtherDefaults(RowValues.id(row));
        }
        return ApiResponse.success(updated);
    }

    @Operation(summary = "Update a send server config and fetch — platform rows are rejected for tenant "
            + "callers; marking it default demotes other defaults")
    @PostMapping("/updateOneAndFetch")
    @DataMask
    @Transactional
    public ApiResponse<Map<String, Object>> updateOneAndFetch(@RequestBody Map<String, Object> row) {
        service.assertWritableInCurrentScope(RowValues.id(row));
        Map<String, Object> updated = modelService.updateOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE);
        if (RowValues.isTrue(row, "isDefault")) {
            service.demoteOtherDefaults(RowValues.id(row));
        }
        return ApiResponse.success(updated);
    }

    @Operation(summary = "Delete a send server config — platform rows are rejected for tenant callers")
    @PostMapping("/deleteById")
    public ApiResponse<Boolean> deleteById(@RequestParam Long id) {
        // Routed through the service (not the generic modelService route) so
        // the config cache is evicted and a cross-scope delete fails with an
        // explanation instead of the ORM's silent no-op.
        return ApiResponse.success(service.deleteById(id));
    }

    @Operation(summary = "Test SMTP connectivity",
            description = "Verify that the system can connect to and authenticate with the SMTP server.")
    @PostMapping("/testConnectivity")
    public ApiResponse<ConnectivityTestResultDTO> testConnectivity(@RequestParam Long id) {
        return ApiResponse.success(service.testConnectivity(id));
    }
}
