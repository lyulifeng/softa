package io.softa.starter.studio.release.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.studio.release.entity.DesignApp;
import io.softa.starter.studio.release.enums.DesignAppStatus;
import io.softa.starter.studio.release.service.DesignAppService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DesignApp Model Controller
 */
@Tag(name = "DesignApp")
@RestController
@RequestMapping("/DesignApp")
public class DesignAppController extends EntityController<DesignAppService, DesignApp, Long> {

    /**
     * Transition the app status with business validation.
     *
     * @param id app ID
     * @param targetStatus target status
     * @return true / Exception
     */
    @Operation(description = "Transition the App status with business validation.")
    @PostMapping(value = "/transitionStatus")
    public ApiResponse<Boolean> transitionStatus(
            @RequestParam @Parameter(description = "App ID") Long id,
            @RequestParam @Parameter(description = "Target App status") DesignAppStatus targetStatus) {
        service.transitionStatus(id, targetStatus);
        return ApiResponse.success(true);
    }

}
