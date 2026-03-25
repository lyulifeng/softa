package io.softa.starter.studio.release.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.studio.release.entity.DesignPortfolio;
import io.softa.starter.studio.release.enums.DesignPortfolioStatus;
import io.softa.starter.studio.release.service.DesignPortfolioService;

/**
 * DesignPortfolio Model Controller
 */
@Tag(name = "DesignPortfolio")
@RestController
@RequestMapping("/DesignPortfolio")
public class DesignPortfolioController extends EntityController<DesignPortfolioService, DesignPortfolio, Long> {

    /**
     * Transition the portfolio status with business validation.
     *
     * @param id portfolio ID
     * @param targetStatus target status
     * @return true / Exception
     */
    @Operation(description = "Transition the Portfolio status with business validation.")
    @PostMapping(value = "/transitionStatus")
    public ApiResponse<Boolean> transitionStatus(
            @RequestParam @Parameter(description = "Portfolio ID") Long id,
            @RequestParam @Parameter(description = "Target Portfolio status") DesignPortfolioStatus targetStatus) {
        service.transitionStatus(id, targetStatus);
        return ApiResponse.success(true);
    }

}
