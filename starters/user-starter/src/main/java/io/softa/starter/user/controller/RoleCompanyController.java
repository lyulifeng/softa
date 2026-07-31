package io.softa.starter.user.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import io.softa.framework.orm.domain.Filters;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.entity.RoleCompany;
import io.softa.starter.user.service.RoleCompanyService;

/**
 * RoleCompany model controller.
 *
 * <p>Every endpoint here exists to route a write through the typed {@link RoleCompanyService}, which
 * publishes {@code RoleGrantChangedEvent} so the cached {@code PermissionInfo} of every user holding the
 * role is evicted. The generic {@code /{modelName}/…} paths write straight through
 * {@code ModelServiceImpl} and skip that, which would leave an administrator's change ineffective until
 * the snapshot expires. Spring routes a literal path over the templated one, so declaring these is what
 * makes them win.
 *
 * <p>{@link #save} is the intended write path and the counterpart of what
 * {@code RoleController.saveWizard} does for the three older grants: it replaces a role's whole set in
 * one call, so the eviction is one event per role rather than one per row. It is a separate endpoint
 * rather than another field on the wizard because the wizard's payload is shared with apps that have no
 * companies at all.
 */
@Tag(name = "RoleCompany")
@RestController
@RequestMapping("/RoleCompany")
public class RoleCompanyController
        extends EntityController<RoleCompanyService, RoleCompany, Long> {

    /**
     * Replace a role's company grant with exactly {@code companyIds}.
     *
     * <p>An empty list clears the grant, which means <b>unrestricted</b> rather than denied — the grant
     * is opt-in, so a role with no rows keeps whatever its other permissions allow. A configuration
     * screen has to say so, because "cleared" reading as "no access" is the natural assumption and the
     * opposite of what happens.
     */
    @Operation(summary = "Replace a role's company grant — empty list clears it (= unrestricted)")
    @PostMapping("/{roleId}/save")
    public ApiResponse<Boolean> save(@PathVariable Long roleId,
                                    @RequestBody(required = false) List<Long> companyIds) {
        service.deleteByFilters(new Filters().eq(RoleCompany::getRoleId, roleId));
        Set<Long> distinct = new LinkedHashSet<>();
        if (companyIds != null) {
            for (Long id : companyIds) {
                if (id != null) {
                    distinct.add(id);
                }
            }
        }
        if (distinct.isEmpty()) {
            return ApiResponse.success(true);
        }
        validateBatchSize(distinct.size());
        List<RoleCompany> rows = new ArrayList<>(distinct.size());
        for (Long companyId : distinct) {
            RoleCompany row = new RoleCompany();
            row.setRoleId(roleId);
            row.setCompanyId(companyId);
            rows.add(row);
        }
        service.createList(rows);
        return ApiResponse.success(true);
    }

    @Operation(summary = "deleteById — routes through the typed service (cache eviction)")
    @PostMapping("/deleteById")
    public ApiResponse<Boolean> deleteById(@RequestParam Long id) {
        return ApiResponse.success(service.deleteById(id));
    }

    @Operation(summary = "deleteByIds — routes through the typed service (cache eviction)")
    @PostMapping("/deleteByIds")
    public ApiResponse<Boolean> deleteByIds(@RequestParam List<Long> ids) {
        return ApiResponse.success(service.deleteByIds(ids));
    }
}
