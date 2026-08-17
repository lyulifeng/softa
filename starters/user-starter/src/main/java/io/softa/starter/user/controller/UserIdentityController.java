package io.softa.starter.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.base.enums.ResponseCode;
import io.softa.framework.base.exception.BusinessException;

/**
 * {@code UserIdentity} has NO API surface — this class exists solely to take the generic one away.
 *
 * <p>The model holds the password hash, its salt and the login identifiers. Nothing outside the
 * server has any business reading or writing them: the credential paths reach it through
 * {@code UserCredentialService} in typed service code, and a person changes their password through
 * the account endpoints, never by writing this row.
 *
 * <p>Registering a model is enough to expose it: {@code ModelController} maps
 * {@code /{modelName}/createOne}, {@code searchPage}, {@code updateOne} … for EVERY registered
 * model, and a platform super-admin bypasses the permission gate — so without this class the
 * generic endpoints would resolve for {@code UserIdentity} and return every person's hash and salt,
 * or accept a planted hash. That is exactly the hole that was found on {@code UserProfile} before
 * the credentials were split out; moving the columns to a new model re-opens it unless the new
 * model is closed the same way.
 *
 * <p>Each of {@code ModelController}'s 28 paths is enumerated as a LITERAL first segment
 * ({@code /UserIdentity/...}), which wins the route over the variable {@code /{modelName}/...} with
 * no ambiguity, for every HTTP method, before the handler touches data. Enumerated rather than
 * pattern-matched on purpose: a path added to {@code ModelController} that is not mirrored here
 * would silently re-open the hole, and an explicit list breaks loudly when the two drift where a
 * wildcard would hide it. Keep in lockstep with {@code UserProfileController}'s identical list.
 */
@Slf4j
@Tag(name = "UserIdentity Controller")
@RestController
@RequestMapping("/UserIdentity")
public class UserIdentityController {

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
}
