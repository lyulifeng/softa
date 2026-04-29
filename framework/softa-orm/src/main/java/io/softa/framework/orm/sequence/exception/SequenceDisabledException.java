package io.softa.framework.orm.sequence.exception;

import io.softa.framework.base.enums.ResponseCode;

/**
 * The sys_sequence row exists but {@code status != 'Active'}. The row was
 * disabled by an admin or DBA and {@code next()} refuses to allocate.
 */
public class SequenceDisabledException extends SequenceException {

    public SequenceDisabledException(String code) {
        super(code, ResponseCode.BAD_REQUEST, "Sequence is disabled: " + code);
    }
}
