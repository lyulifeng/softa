package io.softa.starter.flow.runtime.context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

import static io.softa.starter.flow.runtime.context.RecordModelFixture.MODEL;
import static io.softa.starter.flow.runtime.context.RecordModelFixture.ROUTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordVariablesTest {

    private MockedStatic<ModelManager> modelManager;

    @BeforeEach
    void setUp() {
        modelManager = RecordModelFixture.mockModelManager();
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    @Test
    void anApprovalRoutesOnTheRecordsPlainFieldsButNotItsIdentityBookkeepingApprovalStateOrSensitiveFields() {
        List<String> routed = RecordVariables.routedFields(MODEL).stream().map(MetaField::getFieldName).toList();

        assertEquals(ROUTED, routed);
    }

    @Test
    void theVariablesCarryTheRoutedValuesWithDatesAndTimesAsIsoText() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 42L);
        row.put("days", new BigDecimal("2.5"));
        row.put("leaveType", "Annual");
        row.put("startDate", LocalDate.of(2026, 11, 3));
        row.put("startTime", LocalDateTime.of(2026, 11, 3, 9, 30));
        row.put("departmentId", 7L);
        row.put("reason", null);
        row.put("approvalStatus", "Draft");

        Map<String, Object> variables = RecordVariables.of(MODEL, row);

        assertEquals(Map.of("days", new BigDecimal("2.5"), "leaveType", "Annual", "startDate", "2026-11-03",
                "startTime", "2026-11-03T09:30", "departmentId", 7L), variables);
    }

    @Test
    void aModelTheMetadataDoesNotHoldRoutesOnNothingOfItsOwn() {
        assertTrue(RecordVariables.routedFields("Unknown").isEmpty());
        assertTrue(RecordVariables.of("Unknown", Map.of("days", 3)).isEmpty());
    }
}
