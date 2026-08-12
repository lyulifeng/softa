package io.softa.starter.user.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Page;
import io.softa.framework.orm.domain.SubQueries;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.dto.GetByIdParams;
import io.softa.framework.web.dto.QueryParams;
import io.softa.framework.web.dto.SearchListParams;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.constant.RoleConstant;
import io.softa.starter.user.dto.UserProfileDTO;
import io.softa.starter.user.entity.Role;
import io.softa.starter.user.entity.UserProfile;
import io.softa.starter.user.entity.UserRoleRel;
import io.softa.starter.user.service.RoleService;
import io.softa.starter.user.service.UserProfileService;
import io.softa.starter.user.service.UserRoleRelService;

/**
 * UserProfile Controller.
 *
 * <p>Also shadows the generic {@code /UserProfile} list/detail reads so the platform super-admin
 * sees the profiles of its admin roster. {@code UserProfile} is multiTenant and each profile lives
 * in its account's tenant, so the ORM's automatic narrowing hid every other tenant's admin profile
 * from the platform console. Same window, same bounds, same rationale as
 * {@link UserAccountController} / {@link UserInvitationController}: the roster (accounts holding an
 * admin role in any tenant) plus everything in the super-admin's own tenant — keyed here by
 * {@code userId}, the profile's owning account. Tenant admins are untouched (the ORM already
 * narrows them to their own tenant).
 */
@Slf4j
@Tag(name = "UserProfile Controller")
@RestController
@RequestMapping("/UserProfile")
public class UserProfileController extends EntityController<UserProfileService, UserProfile, Long> {

    private static final String MODEL = "UserProfile";
    /** Holding either of these makes an account part of the roster Ops is responsible for. */
    private static final List<String> ADMIN_ROLE_CODES =
            List.of(RoleConstant.CODE_SUPER_ADMIN, RoleConstant.CODE_TENANT_ADMIN);

    @Autowired
    private ModelService<Long> modelService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleRelService userRoleRelService;

    @Operation(summary = "Get Current User Info", description = "Retrieves the user info of the logged-in user.")
    @GetMapping("/getMyUserInfo")
    public ApiResponse<UserInfo> getMyUserInfo() {
        Long userId = ContextHolder.getContext().getUserId();
        UserInfo userInfo = service.getUserInfo(userId);
        return ApiResponse.success(userInfo);
    }

    @Operation(summary = "Get Current User Profile", description = "Retrieves the profile details of the logged-in user.")
    @GetMapping("/getMyProfile")
    public ApiResponse<Map<String, Object>> getMyProfile() {
        Map<String, Object> profileMap = service.getCurrentUserProfileMap();
        return ApiResponse.success(profileMap);
    }

    @Operation(summary = "Update or Create Current User Profile")
    @PostMapping("/saveMyProfile")
    public ApiResponse<Void> saveMyProfile(@RequestBody @Valid UserProfileDTO myProfileDTO) {
        UserProfile profile = service.getCurrentUserProfile();
        mapDtoToProfile(myProfileDTO, profile);
        service.updateOne(profile);
        return ApiResponse.success();
    }

    /**
     * Typed shadow of the generic {@code /UserProfile/searchPage} — tenant-scoped for everyone,
     * roster-widened for the platform super-admin (see class javadoc).
     */
    @Operation(summary = "Search UserProfile page — tenant-scoped (super-admin also sees the admin roster's profiles)")
    @PostMapping("/searchPage")
    public ApiResponse<Page<Map<String, Object>>> searchPage(@RequestBody(required = false) QueryParams queryParams) {
        QueryParams params = queryParams == null ? new QueryParams() : queryParams;
        FlexQuery flexQuery = QueryParams.convertParamsToFlexQuery(params);
        Page<Map<String, Object>> page = Page.of(params.getPageNumber(), params.getPageSize());
        return ApiResponse.success(inRosterScope(() -> {
            flexQuery.setFilters(scopeByTenant(flexQuery.getFilters()));
            return modelService.searchPage(MODEL, flexQuery, page);
        }));
    }

    @Operation(summary = "Search UserProfile list — same scoping as searchPage")
    @PostMapping("/searchList")
    public ApiResponse<List<Map<String, Object>>> searchList(
            @RequestBody(required = false) SearchListParams searchListParams) {
        SearchListParams params = searchListParams == null ? new SearchListParams() : searchListParams;
        FlexQuery flexQuery = SearchListParams.convertParamsToFlexQuery(params);
        return ApiResponse.success(inRosterScope(() -> {
            flexQuery.setFilters(scopeByTenant(flexQuery.getFilters()));
            return modelService.searchList(MODEL, flexQuery);
        }));
    }

    /**
     * Typed shadow of the generic {@code /UserProfile/getById} — the detail read behind the roster.
     * Membership is re-checked against {@link #scopeToAdminProfiles}, so the super-admin opens
     * exactly what the list shows and an id outside the roster answers like a nonexistent record.
     */
    @Operation(summary = "Get one profile by id — the platform super-admin reads its roster's profiles")
    @PostMapping("/getById")
    public ApiResponse<Map<String, Object>> getById(@RequestBody GetByIdParams getByIdParams) {
        Assert.notNull(getByIdParams.getId(), "The ID of the data to be read cannot be null!");
        ContextHolder.getContext().setEffectiveDate(getByIdParams.getEffectiveDate());
        Long id = IdUtils.formatId(MODEL, getByIdParams.getId());
        SubQueries subQueries = new SubQueries();
        if (getByIdParams.getSubQueries() != null && !getByIdParams.getSubQueries().isEmpty()) {
            subQueries.setQueryMap(getByIdParams.getSubQueries());
        }
        return ApiResponse.success(inRosterScope(() -> {
            if (isPlatformSuperAdmin() && SystemConfig.env.isEnableMultiTenancy() && modelService.count(MODEL,
                    scopeToAdminProfiles(new Filters().eq(ModelConstant.ID, id))) == 0) {
                return null;   // outside the roster — same answer as a nonexistent record
            }
            return modelService.getById(MODEL, id, getByIdParams.getFields(), subQueries, ConvertType.REFERENCE)
                    .orElse(null);
        }));
    }

    /** True when the caller holds the platform super-admin role. */
    private static boolean isPlatformSuperAdmin() {
        Context context = ContextHolder.getContext();
        Set<String> roleCodes = context == null ? null : context.getRoleCodes();
        return roleCodes != null && roleCodes.contains(RoleConstant.CODE_SUPER_ADMIN);
    }

    /**
     * Run the read in a cross-tenant window — super-admin only. Both the roster lookup ({@code Role}
     * / {@code UserRoleRel} are multiTenant) and the query itself must sit inside it, or the ORM ANDs
     * {@code tenant_id = platform} back on and silently drops the roster half. Not {@code @CrossTenant}:
     * that waives isolation for every caller and skips permission checks — see
     * {@link UserAccountController} for the full rationale.
     */
    private <T> T inRosterScope(Supplier<T> read) {
        if (!isPlatformSuperAdmin()) {
            return read.get();
        }
        Context crossTenant = ContextHolder.cloneContext();
        crossTenant.setCrossTenant(true);
        return ContextHolder.callWith(crossTenant, read::get);
    }

    private Filters scopeByTenant(Filters filters) {
        if (!SystemConfig.env.isEnableMultiTenancy()) {
            return filters;   // single-tenant: no tenant dimension
        }
        if (!isPlatformSuperAdmin()) {
            return filters;   // the ORM already narrows this caller's reads to its own tenant
        }
        return scopeToAdminProfiles(filters);
    }

    /**
     * Profiles owned by an account holding an admin role in any tenant, plus every profile in the
     * super-admin's own tenant. Mirrors {@code UserAccountController.scopeToAdminAccounts} /
     * {@code UserInvitationController.scopeToAdminInvitations} — one roster definition, keyed here
     * by the profile's {@code userId}.
     */
    private Filters scopeToAdminProfiles(Filters filters) {
        List<Long> adminRoleIds = roleService.searchList(new Filters().in(Role::getCode, ADMIN_ROLE_CODES))
                .stream().map(Role::getId).toList();
        List<Long> adminUserIds = adminRoleIds.isEmpty() ? List.of()
                : userRoleRelService.searchList(new Filters().in(UserRoleRel::getRoleId, adminRoleIds))
                        .stream().map(UserRoleRel::getUserId).distinct().toList();
        // Empty → sentinel -1L rather than an empty IN, which is ill-defined.
        Filters roster = new Filters()
                .in("userId", adminUserIds.isEmpty() ? List.of(-1L) : adminUserIds);
        Long ownTenant = ContextHolder.getContext() == null ? null : ContextHolder.getContext().getTenantId();
        Filters scope = ownTenant == null ? roster
                : Filters.or(roster, new Filters().eq(ModelConstant.TENANT_ID, ownTenant));
        return filters == null ? scope : Filters.and(filters, scope);
    }

    /**
     * Helper to map UserProfileDTO to UserProfile entity for saving
     */
    private void mapDtoToProfile(UserProfileDTO dto, UserProfile profile) {
        profile.setFullName(dto.getFullName());
        profile.setChineseName(dto.getChineseName());
        profile.setBirthDate(dto.getBirthDate());
        profile.setBirthTime(dto.getBirthTime());
        profile.setBirthCity(dto.getBirthCity());
        profile.setGender(dto.getGender());
        profile.setPhotoId(dto.getPhotoId());
        profile.setLanguage(dto.getLanguage());
        profile.setTimezone(dto.getTimezone());
    }
}