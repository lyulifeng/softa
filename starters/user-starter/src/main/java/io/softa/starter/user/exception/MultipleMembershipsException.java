package io.softa.starter.user.exception;

import java.util.List;

import io.softa.framework.base.exception.BusinessException;
import io.softa.starter.user.dto.MembershipOption;

/**
 * Authentication succeeded but the person must choose a company before a session can be issued.
 *
 * <p>An exception rather than a nullable return so that no caller can forget the case: the old
 * flow issued a session straight after authenticating, and a silently-null account id there would
 * have surfaced much later as a session pointing at nothing.
 *
 * <p>Carries the options so the caller can render the picker without a second round trip — and
 * because it is not an error the person can act on any other way.
 */
public class MultipleMembershipsException extends BusinessException {

    private final transient List<MembershipOption> options;

    public MultipleMembershipsException(List<MembershipOption> options) {
        super("Select a company to continue.");
        this.options = options == null ? List.of() : List.copyOf(options);
    }

    public List<MembershipOption> getOptions() {
        return options;
    }
}
