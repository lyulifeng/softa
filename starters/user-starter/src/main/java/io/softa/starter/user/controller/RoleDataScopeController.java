package io.softa.starter.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.entity.RoleDataScope;
import io.softa.starter.user.service.RoleDataScopeService;

/**
 * RoleDataScope model controller. Overrides {@code /deleteById} /
 * {@code /deleteByIds} so they route through the typed
 * {@link RoleDataScopeService} (which publishes {@code RoleGrantChangedEvent}
 * for per-role cache eviction) instead of the generic {@code ModelController}
 * path, which writes straight through {@code ModelServiceImpl} and skips the
 * event. Other CRUD paths fall through to the generic controller.
 */
@Tag(name = "RoleDataScope")
@RestController
@RequestMapping("/RoleDataScope")
public class RoleDataScopeController
        extends SystemRoleGuardedController<RoleDataScopeService, RoleDataScope, Long> {

    @Override
    protected String modelName() {
        return "RoleDataScope";
    }

    /** Typed service, not ModelService: it publishes RoleGrantChangedEvent for cache eviction.
     *  The mapped endpoint (and its guard call) lives in the base class and is final. */
    @Override
    protected boolean doDeleteById(Long id) {
        return service.deleteById(id);
    }

    @Override
    protected boolean doDeleteByIds(List<Long> ids) {
        return service.deleteByIds(ids);
    }
}
