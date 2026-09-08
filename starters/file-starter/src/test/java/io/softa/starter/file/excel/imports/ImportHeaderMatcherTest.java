package io.softa.starter.file.excel.imports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.ValidationException;
import io.softa.starter.file.dto.ImportFieldDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where the line between "wrong template" and "edited template" is drawn.
 *
 * <p>Both sides of that line matter. Rejecting too little is the bug this exists for — every row
 * failing on a field the file's object does not have. Rejecting too much is worse in operation: an
 * operator who deleted the optional columns they had nothing to put in, or added a column of their
 * own notes, did nothing wrong, and a file they have already filled in would stop importing on an
 * upgrade with no way to see why.
 */
class ImportHeaderMatcherTest {

    private static final List<ImportFieldDTO> TEMPLATE = List.of(
            field("Code", "code", false),
            field("Employee Code", "employeeCode", true),
            field("Type", "type", true),
            field("Remark", "remark", false));

    private static ImportFieldDTO field(String header, String fieldName, boolean required) {
        ImportFieldDTO dto = new ImportFieldDTO();
        dto.setHeader(header);
        dto.setFieldName(fieldName);
        dto.setRequired(required);
        return dto;
    }

    private static void check(List<String> fileHeaders) {
        ImportHeaderMatcher.of(TEMPLATE).assertMatches(fileHeaders, "upload.xlsx");
    }

    @Test
    void anExactHeaderRowPasses() {
        assertThatCode(() -> check(List.of("Code", "Employee Code", "Type", "Remark")))
                .doesNotThrowAnyException();
    }

    @Test
    void droppingAnOptionalColumnPasses() {
        // Missing-only, and nothing required is gone: the operator trimmed what they had no data for.
        assertThatCode(() -> check(List.of("Code", "Employee Code", "Type")))
                .doesNotThrowAnyException();
    }

    @Test
    void addingAColumnOfTheirOwnPasses() {
        assertThatCode(() -> check(List.of("Code", "Employee Code", "Type", "Remark", "Notes")))
                .doesNotThrowAnyException();
    }

    @Test
    void aMissingRequiredColumnIsRejectedOnceInsteadOfOnEveryRow() {
        assertThatThrownBy(() -> check(List.of("Code", "Employee Code", "Remark")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Missing columns: Type");
    }

    @Test
    void aFileThatBothLacksColumnsAndCarriesUndeclaredOnesIsRejected() {
        // Neither half alone is a mismatch; together they are a different sheet, not an edited one.
        assertThatThrownBy(() -> check(List.of("Code", "Employee Code", "Type", "Qualification")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Missing columns: Remark")
                .hasMessageContaining("Unexpected columns: Qualification");
    }

    @Test
    void aHeaderRowWithNothingInCommonIsRejected() {
        assertThatThrownBy(() -> check(List.of("Country", "Currency")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not match the selected import template");
    }

    @Test
    void anEmptyHeaderRowSaysSoRatherThanListingEveryColumn() {
        List<String> blanks = new ArrayList<>(Arrays.asList("", "   ", null));
        assertThatThrownBy(() -> check(blanks))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("has no header row");
    }

    @Test
    void trailingBlankCellsAreNotUndeclaredColumns() {
        // Excel hands back an empty cell for a column that was merely formatted. Counting those as
        // extra columns is not harmless: paired with an optional column the operator dropped, the
        // file would have something missing AND something unexpected, and a correct sheet would be
        // rejected as the wrong template. So the optional column is left out here on purpose.
        List<String> withBlanks = new ArrayList<>(
                Arrays.asList("Code", "Employee Code", "Type", "", null));
        assertThatCode(() -> check(withBlanks)).doesNotThrowAnyException();
    }

    @Test
    void caseAndSurroundingSpaceDoNotDecideWhetherAColumnIsFound() {
        ImportHeaderMatcher matcher = ImportHeaderMatcher.of(TEMPLATE);

        assertThat(matcher.fieldNameFor("employee code")).isEqualTo("employeeCode");
        assertThat(matcher.fieldNameFor(" Type ")).isEqualTo("type");
        assertThat(matcher.fieldNameFor("Employee Code ")).isEqualTo("employeeCode");
        assertThat(matcher.fieldNameFor("Qualification")).isNull();
        assertThat(matcher.fieldNameFor(null)).isNull();
    }

    @Test
    void twoDeclaredColumnsThatOnlyDifferByCaseStayOnExactMatching() {
        // A template like this is a mistake, but guessing which of the two a lenient header meant
        // would write the value into the wrong field — silently, which is worse than not reading it.
        List<ImportFieldDTO> ambiguous = List.of(
                field("Name", "name", false),
                field("NAME", "displayName", false));
        ImportHeaderMatcher matcher = ImportHeaderMatcher.of(ambiguous);

        assertThat(matcher.fieldNameFor("Name")).isEqualTo("name");
        assertThat(matcher.fieldNameFor("NAME")).isEqualTo("displayName");
        assertThat(matcher.fieldNameFor(" name ")).isNull();
    }

    @Test
    void aTemplateDeclaringNoColumnsLeavesTheFileAlone() {
        // Nothing to compare against — the dynamic import wizard can reach here before its fields
        // are configured, and refusing the file would be an opinion the matcher has no basis for.
        assertThatCode(() -> ImportHeaderMatcher.of(List.of())
                .assertMatches(List.of("anything"), "upload.xlsx")).doesNotThrowAnyException();
    }
}
