package io.softa.starter.file.excel.imports;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import io.softa.framework.base.exception.BaseException;
import io.softa.framework.base.i18n.I18n;
import io.softa.framework.web.handler.DuplicateKeyMessageResolver;

/**
 * What an import failure is allowed to tell the person who ran it.
 *
 * <p>An import writes its failures into places a user reads — the Failed Reason column of the
 * downloadable result file, and {@code ImportHistory.errorMessage} on screen. Those are not
 * responses, so they never pass {@code WebExceptionHandler}, which is where the framework already
 * decides what a database error may say. The import lanes called {@code ex.getMessage()} instead,
 * which for a unique violation is the driver's own text:
 *
 * <pre>
 * PreparedStatementCallback; SQL [INSERT INTO employee (code,work_email,…44 columns…) VALUES (?,?,…)];
 * ERROR: duplicate key value violates unique constraint "uk_employee_tenant_code"
 *   Detail: Key (tenant_id, code)=(882561206915698776, UN0001) already exists.
 * </pre>
 *
 * <p>That hands a tenant the table name, every column name, the constraint name and another
 * tenant-scoped identifier in plain text, and tells the person who has to fix the row nothing they
 * can act on. Both halves matter: it is a disclosure and it is useless.
 *
 * <p>The mapping it needed already existed — {@code @Index(unique = true, message = …)} and
 * {@link DuplicateKeyMessageResolver}, which turns the violated constraint name back into the
 * index's own sentence ("An employee with this code already exists."). The import lanes simply
 * never called it.
 *
 * <p>Four tiers, in order:
 * <ol>
 *   <li>a resolvable unique violation &rarr; the index's message;</li>
 *   <li>any other integrity violation &rarr; the same value-free sentence
 *       {@code WebExceptionHandler} uses, so one failure does not read differently depending on
 *       which lane surfaced it;</li>
 *   <li>a {@link BaseException} &rarr; its own message, unchanged. Everything the framework and the
 *       import pipeline raise deliberately is one of these ("The field `Type` is required", "Cannot
 *       find LegalEntity by code=…"), and those sentences are the whole point;</li>
 *   <li>anything else &rarr; a generic sentence. An unplanned exception's message is written for a
 *       developer and can carry anything at all, which is exactly how this bug arrived.</li>
 * </ol>
 *
 * <p>Tiers 2 and 4 log the original at WARN with its stack. The detail is not discarded, it is
 * moved to where the person diagnosing it looks and the tenant does not.
 */
@Slf4j
public final class ImportFailureMessage {

    /** Same sentences as {@code WebExceptionHandler}'s fallbacks — one failure, one wording. */
    private static final String DUPLICATE = "A record with a duplicate value already exists.";
    private static final String INTEGRITY = "The operation violates a data integrity constraint.";
    private static final String UNEXPECTED_ROW =
            "This row could not be saved because of an unexpected error. "
                    + "The details are in the server log.";
    private static final String UNEXPECTED_IMPORT =
            "The import could not be completed because of an unexpected error. "
                    + "The details are in the server log.";

    private ImportFailureMessage() {}

    /**
     * The text for one failed row — the Failed Reason cell.
     *
     * @param failure the exception the row lane caught
     * @return a sentence safe to put in front of a tenant, never null
     */
    public static String forRow(Throwable failure) {
        return of(failure, UNEXPECTED_ROW);
    }

    /**
     * The text for a whole failed import — {@code ImportHistory.errorMessage}.
     *
     * <p>Same tiers, different last resort. A task that never reached the rows (the file download
     * from object storage, a template that would not load) has not failed "a row", and telling the
     * operator it did sends them looking through a spreadsheet for something that is not in it.
     *
     * @param failure the exception the import lane caught
     * @return a sentence safe to put in front of a tenant, never null
     */
    public static String forImport(Throwable failure) {
        return of(failure, UNEXPECTED_IMPORT);
    }

    private static String of(Throwable failure, String unexpected) {
        if (failure == null) {
            return I18n.get(unexpected);
        }

        Optional<DuplicateKeyMessageResolver.DuplicateKeyMessage> friendly =
                DuplicateKeyMessageResolver.resolve(failure);
        if (friendly.isPresent()) {
            log.info("Import hit a unique constraint [{}]", friendly.get().logDetail());
            return friendly.get().userMessage();
        }

        if (failure instanceof DataIntegrityViolationException) {
            // Unmapped: either an index with no registry entry, or a check / not-null / foreign-key
            // violation. Value-free either way — the driver text is what we are here to withhold.
            log.warn("Import violated a database constraint that has no mapped message", failure);
            return I18n.get(failure instanceof DuplicateKeyException ? DUPLICATE : INTEGRITY);
        }

        if (failure instanceof BaseException) {
            // Raised on purpose, phrased for this reader. Passing it through unchanged is the point:
            // "The field `Type` is required" is what lets someone fix their spreadsheet.
            return failure.getMessage();
        }

        log.warn("Import failed with an unexpected exception", failure);
        return I18n.get(unexpected);
    }
}
