package io.softa.starter.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The work contacts an employee record currently holds.
 *
 * <p>The employee record is the single source of these (S-B / D23), so Reset User and Unbind &amp;
 * Re-invite both read them from there rather than taking them from the caller — and both echo them
 * read-only first, because the way to change a number is to edit the record, not the account.
 *
 * @param email  the record's work email, or null when it has none
 * @param mobile the record's work mobile, or null when it has none
 */
@Schema(description = "The work contacts held by an account's employee record")
public record WorkContacts(String email, String mobile) {

    /** Neither channel — an account with no employee record behind it, or a record with no contacts. */
    public static WorkContacts none() {
        return new WorkContacts(null, null);
    }

    /** Whether there is anything to reach this person on. */
    public boolean any() {
        return email != null || mobile != null;
    }
}
