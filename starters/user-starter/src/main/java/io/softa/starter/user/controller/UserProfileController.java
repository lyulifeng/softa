package io.softa.starter.user.controller;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.enums.ResponseCode;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.dto.UserProfileDTO;
import io.softa.starter.user.entity.UserProfile;
import io.softa.starter.user.service.UserProfileService;

/**
 * The ONLY API surface of {@code UserProfile}: three endpoints that touch the caller's own row.
 *
 * <p>Deliberately NOT an {@code EntityController}. A person is global rather than tenant-scoped, so
 * the generic CRUD surface would reach across every tenant: list reads would return every person in
 * the system, and {@code updateOne} would let any caller rewrite anyone's details.
 *
 * <p>The credentials themselves are no longer here — they moved to {@code UserIdentity}, which is
 * closed off the same way — so what this protects now is cross-tenant PII rather than a password
 * hash. The surface stays shut regardless: this release ships no people-directory feature, and
 * opening a browsable surface is a product decision with its own permission model, not a side
 * effect of moving credential columns. There is no list endpoint at all: a person is only ever
 * reached from their own session, or internally from the membership ({@code UserAccount}) in typed
 * service code.
 *
 * <p>The three self endpoints keep their historical URLs so the frontend session bootstrap and the
 * personal-settings page need no change.
 */
@Slf4j
@Tag(name = "UserProfile Controller")
@RestController
@RequestMapping("/UserProfile")
public class UserProfileController {

    @Autowired
    private UserProfileService service;

    /**
     * Every generic CRUD path that {@code ModelController} would otherwise expose for this model,
     * explicitly reclaimed here and answered with 404.
     *
     * <p>This is what actually closes the surface. Not extending {@code EntityController} is not
     * enough on its own: {@code ModelController} maps {@code /{modelName}/createOne} etc. for EVERY
     * registered model, and a platform super-admin bypasses the permission gate — so the generic
     * endpoints would still resolve and return, or rewrite, every person in every tenant.
     * Listing each path as a LITERAL first segment ({@code /UserProfile/...})
     * wins the route over {@code ModelController}'s variable first segment ({@code /{modelName}/...})
     * with no ambiguity, for every HTTP method, before the handler ever touches data.
     *
     * <p>Mirror of {@code ModelController}'s endpoint set — kept in lockstep with it. A path added
     * there that is not added here would silently re-open a hole, which is why it is enumerated
     * rather than pattern-matched: an explicit list breaks loudly when the two drift, a wildcard
     * would not.
     */
    @RequestMapping({
            "/createOne", "/createOneAndFetch", "/createList", "/createListAndFetch",
            "/getById", "/getByIds", "/getCopyableFields", "/getDefaultValues",
            "/getUnmaskedField", "/getUnmaskedFields",
            "/updateOne", "/updateOneAndFetch", "/updateList", "/updateListAndFetch", "/updateByFilter",
            "/deleteById", "/deleteByIds",
            "/copyById", "/copyByIdAndFetch", "/copyByIds", "/copyByIdsAndFetch",
            "/searchPage", "/searchList", "/searchName", "/searchSimpleAgg", "/searchPivot", "/count",
            "/onChange/{fieldName}"
    })
    public void notExposed(HttpServletRequest request) {
        // REQUEST_NOT_FOUND so the response is byte-for-byte what a genuinely unmapped path returns
        // (code 404, "Resource not found") — a caller cannot tell "reclaimed and refused" from
        // "never existed", which is the whole point: the model is not part of the API.
        throw new BusinessException(ResponseCode.REQUEST_NOT_FOUND,
                "No endpoint " + request.getMethod() + " " + request.getRequestURI());
    }

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
     * The write whitelist. Only what a person may change about themselves is copied — the DTO is
     * the boundary, so credential fields cannot arrive through this endpoint no matter what the
     * payload carries.
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
