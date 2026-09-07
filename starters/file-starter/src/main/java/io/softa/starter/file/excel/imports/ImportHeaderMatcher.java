package io.softa.starter.file.excel.imports;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.softa.framework.base.exception.ValidationException;
import io.softa.starter.file.dto.ImportFieldDTO;

/**
 * Pairs a sheet's header row with the template the operator picked.
 *
 * <p>Reading was column-by-column and forgiving in both directions: a header the template does not
 * declare was dropped without a word, and a column the template declares but the file lacks simply
 * never reached the row. Neither is visible as itself. What the operator saw instead was every row
 * failing on {@code The field Type is required} — a field that does not even belong to the object
 * they were importing, because the file was written from a different template. Ten of the employee
 * child templates open with the same three columns (Code / Employee Code / Employee Name), so
 * picking the wrong one is easy and, until here, indistinguishable from filling one in badly.
 *
 * <p>The header row settles it before a single data row is read, and it is the only place that can:
 * by the time a row fails validation the mismatch has already been rewritten as a per-field error.
 *
 * <p>What counts as a mismatch is deliberately narrow — deleting an optional column and adding a
 * scratch column are both things operators legitimately do to a downloaded template:
 * <ul>
 *   <li><b>nothing matched</b> — no shared column at all, so this is not the file's template;</li>
 *   <li><b>a required column is missing</b> — the import cannot succeed, and saying so once beats
 *       saying it on every row;</li>
 *   <li><b>columns missing AND unexpected columns present</b> — a file that both lacks declared
 *       columns and carries undeclared ones has a different shape, not a trimmed one.</li>
 * </ul>
 * Missing-only (optional columns dropped) and extra-only (a note column added) still import.
 */
public final class ImportHeaderMatcher {

    /** Declared header -> field name, as written on the template. */
    private final Map<String, String> byExactHeader = new LinkedHashMap<>();

    /** Normalized header -> field name, for headers that normalize uniquely. */
    private final Map<String, String> byNormalizedHeader = new LinkedHashMap<>();

    /**
     * Normalized headers two declared columns share. They stay out of the lenient lane: picking
     * either of the two fields would be a guess, so such a header only ever matches exactly.
     */
    private final Set<String> ambiguousNormalizedHeaders = new LinkedHashSet<>();

    /** Declared headers whose column the template marks required. */
    private final Set<String> requiredHeaders = new LinkedHashSet<>();

    private ImportHeaderMatcher(List<ImportFieldDTO> importFields) {
        if (importFields == null) {
            return;
        }
        for (ImportFieldDTO importField : importFields) {
            String header = importField.getHeader();
            if (header == null || header.isBlank()) {
                continue;
            }
            byExactHeader.put(header, importField.getFieldName());
            String normalized = normalize(header);
            String claimed = byNormalizedHeader.putIfAbsent(normalized, importField.getFieldName());
            if (claimed != null && !claimed.equals(importField.getFieldName())) {
                ambiguousNormalizedHeaders.add(normalized);
            }
            if (Boolean.TRUE.equals(importField.getRequired())) {
                requiredHeaders.add(header);
            }
        }
    }

    public static ImportHeaderMatcher of(List<ImportFieldDTO> importFields) {
        return new ImportHeaderMatcher(importFields);
    }

    /**
     * The field a sheet column feeds, or {@code null} when the template declares no such column.
     *
     * <p>Exact first, then case- and space-insensitively. The lenient pass is what keeps a header
     * carrying a trailing space or a non-breaking one (both survive a copy-paste out of a browser)
     * from silently dropping its whole column — the failure it used to produce was the same
     * "required field" noise this class exists to end.
     */
    public String fieldNameFor(String header) {
        if (header == null) {
            return null;
        }
        String exact = byExactHeader.get(header);
        if (exact != null) {
            return exact;
        }
        String normalized = normalize(header);
        return ambiguousNormalizedHeaders.contains(normalized) ? null : byNormalizedHeader.get(normalized);
    }

    /**
     * Rejects the file when its header row says it was written from a different template.
     *
     * @param fileHeaders the header cells as read from the sheet, in column order
     * @param fileName the uploaded file's name, for the message
     * @throws ValidationException when the headers do not match the template
     */
    public void assertMatches(Collection<String> fileHeaders, String fileName) {
        List<String> present = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        Set<String> matchedFields = new LinkedHashSet<>();
        if (fileHeaders != null) {
            for (String fileHeader : fileHeaders) {
                if (fileHeader == null || fileHeader.isBlank()) {
                    // Excel hands back trailing empty cells for a formatted-but-unused column; they
                    // are not columns the operator wrote, so they are neither matched nor extra.
                    continue;
                }
                present.add(fileHeader);
                String fieldName = fieldNameFor(fileHeader);
                if (fieldName == null) {
                    extra.add(fileHeader);
                } else {
                    matchedFields.add(fieldName);
                }
            }
        }

        List<String> missing = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        byExactHeader.forEach((header, fieldName) -> {
            if (matchedFields.contains(fieldName)) {
                return;
            }
            missing.add(header);
            if (requiredHeaders.contains(header)) {
                missingRequired.add(header);
            }
        });

        if (present.isEmpty()) {
            throw new ValidationException(
                    "The uploaded file `{0}` has no header row. Fill in the template downloaded for "
                            + "this import and upload that file.", fileName);
        }
        if (byExactHeader.isEmpty()) {
            // Nothing declared to match against — leave the file to the rest of the pipeline.
            return;
        }
        if (!matchedFields.isEmpty() && missingRequired.isEmpty()
                && (missing.isEmpty() || extra.isEmpty())) {
            return;
        }
        throw new ValidationException(
                "The uploaded file `{0}` does not match the selected import template.{1}{2} "
                        + "Re-select the template this file was filled in from, or download the "
                        + "selected template and fill that in.",
                fileName, describe(" Missing columns: ", missing), describe(" Unexpected columns: ", extra));
    }

    private static String describe(String label, List<String> headers) {
        return headers.isEmpty() ? "" : label + String.join(", ", headers) + ".";
    }

    /**
     * Case and surrounding space carry no meaning in a header. The non-breaking space is replaced
     * first on purpose: {@code String#strip} leaves it alone (it is not whitespace to
     * {@link Character#isWhitespace}), and it is exactly what a header pasted out of a web page
     * carries.
     */
    private static String normalize(String header) {
        return header.replace('\u00A0', ' ').strip().toLowerCase(Locale.ROOT);
    }
}
