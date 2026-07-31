package io.softa.starter.tenant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.web.response.ApiResponse;

/**
 * Closes every generic write endpoint of {@code /TenantSubscription}.
 *
 * <p>Every business column on that table is a projection of the period rows, so there is no such thing as
 * a legitimate write from outside: the row is created when a tenant is provisioned and rewritten only by
 * the projection refresh. Ops changes <b>periods</b>; the current state follows.
 *
 * <p>Leaving these open would be worse than untidy. Authorization reads this row, so a hand-edited
 * {@code planId} or {@code subscriptionStatus} is not a display glitch — it is a granted module. And writing
 * {@code projectedForDate} would defeat the staleness check that makes the projection safe to read at all.
 *
 * <p>Reads are untouched: the generic {@code searchList} / {@code getById} are what the tenant list and
 * detail page use, including the cascaded columns that render the current plan.
 */
@Tag(name = "TenantSubscription")
@RestController
@RequestMapping("/TenantSubscription")
public class TenantSubscriptionController {

    private static final String READ_ONLY =
            "A tenant subscription is derived from its periods and cannot be edited directly — "
                    + "record or change a subscription period instead.";

    @PostMapping({"/createOne", "/createOneAndFetch", "/createList", "/createListAndFetch"})
    public ApiResponse<Void> rejectCreate(@RequestBody(required = false) Object ignored) {
        // Provisioning creates the row; nothing else should.
        throw new BusinessException(READ_ONLY);
    }

    @PostMapping({"/updateOne", "/updateOneAndFetch", "/updateList", "/updateListAndFetch",
            "/updateByFilter"})
    public ApiResponse<Void> rejectUpdate(@RequestBody(required = false) Object ignored) {
        throw new BusinessException(READ_ONLY);
    }

    @PostMapping({"/copyById", "/copyByIdAndFetch", "/copyByIds", "/copyByIdsAndFetch"})
    public ApiResponse<Void> rejectCopy(@RequestBody(required = false) Object ignored) {
        throw new BusinessException("A tenant subscription cannot be copied — it belongs to one tenant.");
    }

    @PostMapping({"/deleteById", "/deleteByIds", "/deleteBySliceId"})
    public ApiResponse<Void> rejectDelete(@RequestParam(required = false) Long id,
                                          @RequestBody(required = false) Object ignored) {
        // Deleting it would strand TenantInfo.subscriptionId and leave the tenant with no projection to
        // read. Tenants go away by being closed, not by losing their subscription row.
        throw new BusinessException("A tenant subscription cannot be deleted — close the tenant instead.");
    }
}
