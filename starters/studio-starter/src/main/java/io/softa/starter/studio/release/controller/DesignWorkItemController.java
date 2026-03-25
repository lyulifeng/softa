package io.softa.starter.studio.release.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.studio.release.dto.ModelChangesDTO;
import io.softa.starter.studio.release.entity.DesignWorkItem;
import io.softa.starter.studio.release.service.DesignWorkItemService;

/**
 * DesignWorkItem Model Controller
 */
@Tag(name = "DesignWorkItem")
@RestController
@RequestMapping("/DesignWorkItem")
public class DesignWorkItemController extends EntityController<DesignWorkItemService, DesignWorkItem, Long> {

    /**
     * Mark the WorkItem as READY.
     *
     * @param id WorkItem ID
     * @return true / Exception
     */
    @Operation(description = "Mark the WorkItem as READY (from IN_PROGRESS).")
    @PostMapping(value = "/readyWorkItem")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<Boolean> readyWorkItem(@RequestParam Long id) {
        service.readyWorkItem(id);
        return ApiResponse.success(true);
    }

    /**
     * Complete the WorkItem, setting closedTime and transitioning status to DONE.
     *
     * @param id WorkItem ID
     * @return true / Exception
     */
    @Operation(description = "Complete the WorkItem, setting closedTime and transitioning status to DONE.")
    @PostMapping(value = "/doneWorkItem")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<Boolean> doneWorkItem(@RequestParam Long id) {
        service.doneWorkItem(id);
        return ApiResponse.success(true);
    }

    /**
     * Preview all metadata changes accumulated under this WorkItem.
     *
     * @param id WorkItem ID
     * @return list of model-level change summaries
     */
    @Operation(description = "Preview all metadata changes accumulated under this WorkItem.")
    @GetMapping(value = "/previewChanges")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<List<ModelChangesDTO>> previewChanges(@RequestParam Long id) {
        return ApiResponse.success(service.previewWorkItemChanges(id));
    }

    /**
     * Preview DDL SQL generated from the metadata changes of this WorkItem.
     * The returned SQL can be copied to a database client for execution.
     *
     * @param id WorkItem ID
     * @return DDL SQL string
     */
    @Operation(description = "Preview DDL SQL generated from WorkItem metadata changes, ready for copy to database client.")
    @GetMapping(value = "/previewDDL")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<String> previewDDL(@RequestParam Long id) {
        return ApiResponse.success(service.previewWorkItemDDL(id));
    }

    /**
     * Cancel the WorkItem.
     */
    @Operation(description = "Cancel the WorkItem (from IN_PROGRESS, READY, or DEFERRED).")
    @PostMapping(value = "/cancelWorkItem")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<Boolean> cancelWorkItem(@RequestParam Long id) {
        service.cancelWorkItem(id);
        return ApiResponse.success(true);
    }

    /**
     * Defer the WorkItem.
     */
    @Operation(description = "Defer the WorkItem (from IN_PROGRESS).")
    @PostMapping(value = "/deferWorkItem")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<Boolean> deferWorkItem(@RequestParam Long id) {
        service.deferWorkItem(id);
        return ApiResponse.success(true);
    }

    /**
     * Reopen a completed, cancelled, or deferred WorkItem back to IN_PROGRESS.
     */
    @Operation(description = "Reopen a DONE, CANCELLED, or DEFERRED WorkItem back to IN_PROGRESS.")
    @PostMapping(value = "/reopenWorkItem")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<Boolean> reopenWorkItem(@RequestParam Long id) {
        service.reopenWorkItem(id);
        return ApiResponse.success(true);
    }

    /**
     * Merge a DONE WorkItem into the latest DRAFT version of the same App.
     * If no DRAFT version exists, one is automatically created.
     *
     * @param id WorkItem ID
     * @return the Version ID that the WorkItem was merged into
     */
    @Operation(description = "Merge a DONE WorkItem into the latest DRAFT version. Auto-creates a DRAFT version if none exists.")
    @PostMapping(value = "/mergeToLatestVersion")
    @Parameter(name = "id", description = "WorkItem ID")
    public ApiResponse<Long> mergeToLatestVersion(@RequestParam Long id) {
        return ApiResponse.success(service.mergeToLatestVersion(id));
    }

}
