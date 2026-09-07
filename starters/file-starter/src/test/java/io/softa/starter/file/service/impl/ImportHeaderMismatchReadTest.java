package io.softa.starter.file.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.ValidationException;
import io.softa.starter.file.dto.ImportFieldDTO;
import io.softa.starter.file.excel.imports.ImportHeaderMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The header check driven the way production drives it — through the real reader, on real bytes.
 *
 * <p>{@link io.softa.starter.file.excel.imports.ImportHeaderMatcherTest} settles what counts as a
 * mismatch. It cannot settle the two things that decide whether any of it reaches the operator:
 * that {@code invokeHeadMap} is called at all before the first row, and that an exception thrown
 * from inside a reader callback comes back out of {@code doRead} as itself rather than wrapped in
 * the reader's own exception — which would replace the message with a stack-trace-shaped one and
 * put the mismatch back out of reach.
 */
class ImportHeaderMismatchReadTest {

    /** The template the operator picked: Employee Certifications & Attachments. */
    private static final List<ImportFieldDTO> CERTIFICATIONS = List.of(
            field("Code", "code", false),
            field("Employee Code", "employeeCode", true),
            field("Employee Name", "employeeName", false),
            field("Type", "type", true),
            field("Name", "name", true));

    /** The file they actually uploaded: Employee Professional Qualifications. */
    private static final List<String> QUALIFICATION_HEADERS = List.of(
            "Code", "Employee Code", "Employee Name", "Qualification", "Other Description");

    private final ImportServiceImpl importService = new ImportServiceImpl();

    private static ImportFieldDTO field(String header, String fieldName, boolean required) {
        ImportFieldDTO dto = new ImportFieldDTO();
        dto.setHeader(header);
        dto.setFieldName(fieldName);
        dto.setRequired(required);
        return dto;
    }

    /** A one-sheet workbook with the given header row and one data row under it. */
    private static byte[] workbookOf(List<String> headers, List<String> row) throws Exception {
        List<List<String>> heads = headers.stream().map(List::of).toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter writer = FesodSheet.write(out).build()) {
            WriteSheet sheet = FesodSheet.writerSheet(0, "Sheet1").head(heads).build();
            writer.write(List.of(new ArrayList<>(row)), sheet);
            writer.finish();
        }
        return out.toByteArray();
    }

    private List<Map<String, Object>> read(List<ImportFieldDTO> template, byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return importService.extractDataFromExcel(
                    ImportHeaderMatcher.of(template), "upload.xlsx", in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aFileWrittenFromAnotherTemplateIsRejectedBeforeAnyRowIsRead() throws Exception {
        byte[] bytes = workbookOf(QUALIFICATION_HEADERS,
                List.of("", "E1000001", "Tina", "ACCA/CFA", "member since 2019"));

        assertThatThrownBy(() -> read(CERTIFICATIONS, bytes))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not match the selected import template")
                // Both halves of the evidence, so the operator can see which file they grabbed.
                .hasMessageContaining("Missing columns: Type, Name")
                .hasMessageContaining("Unexpected columns: Qualification, Other Description")
                // And never the old symptom, which named a field this object does not have.
                .hasMessageNotContaining("is required");
    }

    @Test
    void theRightFileStillReads() throws Exception {
        byte[] bytes = workbookOf(List.of("Code", "Employee Code", "Employee Name", "Type", "Name"),
                List.of("CERT001", "E1000001", "Tina", "Certificate", "First Aid"));

        List<Map<String, Object>> rows = read(CERTIFICATIONS, bytes);

        assertThat(rows).singleElement().satisfies(row ->
                assertThat(row).containsEntry("employeeCode", "E1000001")
                        .containsEntry("type", "Certificate")
                        .containsEntry("name", "First Aid"));
    }

    @Test
    void aHeaderThatOnlyDiffersByCaseOrSpacingStillFeedsItsColumn() throws Exception {
        // What a header carries after a trip through a spreadsheet and a browser: a trailing space,
        // a non-breaking one, a changed case. Each of these used to drop the whole column silently,
        // and the row then failed on the very "required" error this change exists to stop producing.
        byte[] bytes = workbookOf(
                List.of("Code", "employee code", "Employee Name ", "Type\u00A0", "NAME"),
                List.of("CERT001", "E1000001", "Tina", "Certificate", "First Aid"));

        List<Map<String, Object>> rows = read(CERTIFICATIONS, bytes);

        assertThat(rows).singleElement().satisfies(row ->
                assertThat(row).containsEntry("employeeCode", "E1000001")
                        .containsEntry("employeeName", "Tina")
                        .containsEntry("type", "Certificate")
                        .containsEntry("name", "First Aid"));
    }

    @Test
    void aScratchColumnAddedToTheRightTemplateIsStillImported() throws Exception {
        // Extra alone is not a mismatch: operators annotate the sheet they were sent.
        List<String> headers = new ArrayList<>(
                Arrays.asList("Code", "Employee Code", "Employee Name", "Type", "Name", "Notes"));
        byte[] bytes = workbookOf(headers,
                List.of("CERT001", "E1000001", "Tina", "Certificate", "First Aid", "chase HR"));

        List<Map<String, Object>> rows = read(CERTIFICATIONS, bytes);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("name", "First Aid");
            assertThat(row).as("the undeclared column feeds nothing").hasSize(5);
        });
    }
}
