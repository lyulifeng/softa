package io.softa.starter.file.excel.imports;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.constant.FileConstant;
import io.softa.framework.orm.meta.MetaIndex;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportDataDTO;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.enums.ImportRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The column the tenant actually downloads.
 *
 * <p>{@link ImportFailureMessageTest} settles what the sanitiser returns. It cannot settle whether
 * the row-by-row lane calls it — and that is the whole bug: the sanitising already existed for
 * responses ({@code WebExceptionHandler}), and the import lane wrote {@code ex.getMessage()}
 * straight into Failed Reason instead. A unit test of the sanitiser would have passed throughout.
 *
 * <p>So this one drives {@link ImportPersistenceService#persist} with a persistence layer that
 * throws exactly what PostgreSQL throws, and reads the cell the operator opens the file to.
 */
class FailedReasonWithholdsDatabaseDetailTest {

    private static final String LEAKY_DRIVER_TEXT =
            "PreparedStatementCallback; SQL [INSERT INTO employee (code,work_email,full_name,"
                    + "tenant_id) VALUES (?,?,?,?)]; ERROR: duplicate key value violates unique "
                    + "constraint \"uk_employee_tenant_code\"\n  Detail: Key (tenant_id, code)="
                    + "(882561206915698776, UN0001) already exists.";

    private static ImportTemplateDTO skipExceptionTemplate() {
        ImportTemplateDTO template = new ImportTemplateDTO();
        template.setModelName("Employee");
        template.setImportRule(ImportRule.ONLY_CREATE);
        // The lane under test: without it a bad row fails the whole task and the message goes out
        // through the response instead, which was never the leaking path.
        template.setSkipException(true);
        return template;
    }

    private static ImportDataDTO twoRowsOneOfWhichClashes() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("code", "UN0001")));
        rows.add(new LinkedHashMap<>(Map.of("code", "UN0002")));
        ImportDataDTO data = new ImportDataDTO();
        data.setRows(rows);
        data.setOriginalRows(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("code", "UN0001")),
                new LinkedHashMap<>(Map.of("code", "UN0002")))));
        return data;
    }

    private static ImportPersistenceService serviceThatRejects(String clashingCode) {
        ModelService<?> modelService = mock(ModelService.class);
        when(modelService.createList(anyString(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> batch = invocation.getArgument(1);
            boolean clashes = batch.stream().anyMatch(row -> clashingCode.equals(row.get("code")));
            if (clashes) {
                throw new DuplicateKeyException(LEAKY_DRIVER_TEXT,
                        new SQLException("ERROR: duplicate key value violates unique constraint "
                                + "\"uk_employee_tenant_code\"", "23505"));
            }
            // createList returns List<K>, not a boolean — returning the wrong type here makes
            // Mockito throw, which the sanitiser's last tier catches and reports as "unexpected".
            return List.of();
        });
        ImportPersistenceService service = new ImportPersistenceService();
        ReflectionTestUtils.setField(service, "modelService", modelService);
        return service;
    }

    private static MetaIndex employeeCodeIndex() {
        MetaIndex index = mock(MetaIndex.class);
        when(index.getModelName()).thenReturn("Employee");
        when(index.getIndexFields()).thenReturn(List.of("tenantId", "code"));
        when(index.getMessage()).thenReturn("An employee with this code already exists.");
        return index;
    }

    @Test
    void theFailedReasonCellCarriesTheIndexsSentenceAndNoDatabaseDetail() {
        ImportDataDTO data = twoRowsOneOfWhichClashes();
        MetaIndex mapped = employeeCodeIndex();

        try (MockedStatic<ModelManager> registry = Mockito.mockStatic(ModelManager.class)) {
            registry.when(() -> ModelManager.getIndex("uk_employee_tenant_code")).thenReturn(mapped);

            serviceThatRejects("UN0001").persist(skipExceptionTemplate(), data);
        }

        assertThat(data.getFailedRows()).singleElement().satisfies(failed -> {
            assertThat(failed).containsEntry("code", "UN0001");
            String reason = String.valueOf(failed.get(FileConstant.FAILED_REASON));
            assertThat(reason).isEqualTo("An employee with this code already exists.");
            assertThat(reason)
                    .doesNotContain("INSERT")
                    .doesNotContain("work_email")
                    .doesNotContain("uk_employee_tenant_code")
                    .doesNotContain("882561206915698776");
        });

        assertThat(data.getRows()).as("the row that did not clash still imports")
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("code", "UN0002"));
    }
}
