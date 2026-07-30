package io.softa.framework.web.controller;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.orm.annotation.DataMask;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.response.ApiResponse;

/**
 * Version-slice operations for timeline models, sharing the dynamic `/{modelName}` URL space
 * with {@link ModelController} (which carries the universal CRUD / search / copy surface — a
 * timeline model uses both transparently, controllers compose by URL, not by model ownership).
 * Capability gating stays in the versioning seam: on a non-timeline model these endpoints are
 * rejected by {@code VersioningStrategy}'s default methods, never by controller-level checks.
 */
@Tag(name = "Timeline Model APIs", description = "Version-slice operations for timeline models: "
        + "add versions, delete slices, terminate/reopen the timeline. Rejected for non-timeline models.")
@RestController
@RequestMapping("/{modelName}")
public class TimelineModelController {

    private final ModelService<?> modelService;

    public TimelineModelController(ModelService<?> modelService) {
        this.modelService = modelService;
    }

    /**
     * Delete one slice of the timeline model by `sliceId`, the primary key of the timeline model.
     *
     * @param modelName model name
     * @param sliceId   data id
     * @return True / Exception
     */
    @PostMapping(value = "/deleteBySliceId")
    @Operation(description = "Delete one slice of the timeline model by `sliceId`.")
    @Parameter(name = "sliceId", description = "`sliceId` of the timeline slice data to delete.", schema = @Schema(type = "string"))
    public ApiResponse<Boolean> deleteBySliceId(@PathVariable String modelName, @RequestParam Serializable sliceId) {
        return ApiResponse.success(modelService.deleteBySliceId(modelName, sliceId));
    }

    /**
     * Add a version (slice) to an existing timeline entity. Counterpart of `deleteBySliceId`.
     *
     * @param modelName model name
     * @param row       version data, carrying the existing entity's `id`
     * @return the new version's sliceId
     */
    @PostMapping(value = "/addVersion")
    @Operation(description = "Timeline models only: add a version (slice) to an EXISTING entity. "
            + "The row must carry the entity's `id`; `effectiveStartDate` defaults to the context effective "
            + "date. Adjacent slices are split/corrected automatically. Returns the new version's `sliceId` "
            + "(the existing sliceId when a same-start slice is corrected in place).")
    @DataMask
    public ApiResponse<Serializable> addVersion(@PathVariable String modelName,
                                                @RequestBody Map<String, Object> row) {
        return ApiResponse.success(modelService.addVersion(modelName, row));
    }

    /**
     * Add a version like `addVersion`, then fetch the version row by its sliceId.
     *
     * @param modelName model name
     * @param row       version data, carrying the existing entity's `id`
     * @return the created/corrected version row, including sliceId and effective dates
     */
    @PostMapping(value = "/addVersionAndFetch")
    @Operation(description = "Timeline models only: add a version to an EXISTING entity and fetch the "
            + "version row (by its `sliceId`, across the timeline).")
    @DataMask
    public ApiResponse<Map<String, Object>> addVersionAndFetch(@PathVariable String modelName,
                                                               @RequestBody Map<String, Object> row) {
        return ApiResponse.success(modelService.addVersionAndFetch(modelName, row, ConvertType.REFERENCE));
    }

    /**
     * Set the end date of a timeline entity's LAST slice — terminate or reopen the timeline.
     *
     * @param modelName model name
     * @param id        logical id of the timeline entity
     * @param endDate   new end date of the last slice; `9999-12-31` reopens
     * @return true when the tail row was written
     */
    @PostMapping(value = "/setEndDate")
    @Operation(description = "Timeline models only: set the end date of the entity's LAST slice. "
            + "An `endDate` before 9999-12-31 terminates the timeline (as-of reads after it return "
            + "nothing); passing 9999-12-31 reopens it. Must not precede the last slice's start date "
            + "— delete trailing versions first to terminate earlier. A later `addVersion` starting "
            + "after a terminated end date revives the entity as a new segment, leaving a gap.")
    @Parameters({
            @Parameter(name = "id", description = "Logical id of the timeline entity.", schema = @Schema(type = "string")),
            @Parameter(name = "endDate", description = "New end date (ISO date) of the LAST slice; `9999-12-31` reopens.")
    })
    public ApiResponse<Boolean> setEndDate(@PathVariable String modelName,
                                           @RequestParam Serializable id,
                                           @RequestParam LocalDate endDate) {
        return ApiResponse.success(modelService.setEndDate(modelName, id, endDate));
    }
}
