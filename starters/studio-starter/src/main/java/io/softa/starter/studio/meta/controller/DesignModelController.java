package io.softa.starter.studio.meta.controller;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.base.utils.Assert;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.studio.meta.entity.DesignModel;
import io.softa.starter.studio.meta.service.DesignModelService;

/**
 * DesignModel Model Controller
 */
@Tag(name = "DesignModel")
@RestController
@RequestMapping("/DesignModel")
public class DesignModelController extends AbstractDesignWriteController<DesignModelService, DesignModel> {

    @Override
    protected String modelName() {
        return "DesignModel";
    }

    @Override
    protected String renameKeyField() {
        return "modelName";
    }

    @Override
    protected void onCreate(Map<String, Object> row) {
        rejectProjection(row);
        super.onCreate(row);
    }

    @Override
    protected void onUpdate(Map<String, Object> row) {
        rejectProjection(row);
        super.onUpdate(row);
    }

    /**
     * Studio does not support authoring projection models (read-only models over a table
     * another model owns): the no-code lane has no owner to validate against and its DDL
     * preview/deploy semantics for a table-less model are undesigned. The column exists on
     * {@code design_model} only as the structural mirror the cross-lane checksum requires.
     */
    private static void rejectProjection(Map<String, Object> row) {
        Object value = row.get("projection");
        Assert.notTrue(value != null && Boolean.parseBoolean(value.toString()),
                "Studio does not support authoring projection models yet — declare them in code"
                        + " via @Model(projection = true).");
    }

    /**
     * Preview the DDL SQL of model, including table creation and index creation
     *
     * @param id Model ID
     * @return Model DDL SQL
     */
    @Operation(description = "Preview the DDL SQL of model, including table creation and index creation")
    @GetMapping(value = "/previewDDL")
    @Parameter(name = "id", description = "Model ID")
    public ApiResponse<String> previewDDL(@RequestParam Long id) {
        return ApiResponse.success(service.previewDDL(id));
    }
}
