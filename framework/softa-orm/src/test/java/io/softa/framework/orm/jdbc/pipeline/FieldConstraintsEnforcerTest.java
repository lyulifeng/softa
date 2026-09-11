package io.softa.framework.orm.jdbc.pipeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.domain.EvalContext;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.FieldConstraints;
import io.softa.framework.orm.meta.MetaField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The conditional rules against real write shapes — above all the update whose patch does not carry
 * the field being judged. That is the hole the frontend cannot reproduce (the form re-evaluates on
 * every keystroke) and only an import or a direct call can open.
 */
class FieldConstraintsEnforcerTest {

    private static final Map<String, FieldType> TYPES = Map.of(
            "reason", FieldType.OPTION, "reasonDescription", FieldType.STRING, "status", FieldType.OPTION,
            "amount", FieldType.BIG_DECIMAL, "startDate", FieldType.DATE, "endDate", FieldType.DATE,
            "checkInStatus", FieldType.OPTION, "lateMinutes", FieldType.INTEGER, "costCentreId", FieldType.LONG);

    private static MetaField field(String name, FieldConstraints constraints) {
        MetaField f = new MetaField();
        ReflectionTestUtils.setField(f, "modelName", "EmpChangeRequest");
        ReflectionTestUtils.setField(f, "fieldName", name);
        ReflectionTestUtils.setField(f, "fieldType", TYPES.get(name));
        ReflectionTestUtils.setField(f, "constraints", constraints);
        return f;
    }

    private static FieldConstraints c(String requiredWhen, String hiddenWhen, String readonlyWhen, String invalidWhen, String message) {
        return FieldConstraints.of(null, null, null, message, requiredWhen, hiddenWhen, readonlyWhen, invalidWhen, "EmpChangeRequest.x");
    }

    private static FieldConstraintsEnforcer enforcer(AccessType type, MetaField... fields) {
        EvalContext ctx = new EvalContext(type, 7L, LocalDate.of(2026, 9, 11), LocalDateTime.of(2026, 9, 11, 9, 0));
        return new FieldConstraintsEnforcer("EmpChangeRequest", type, List.of(fields), TYPES::get, ctx);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            row.put((String) kv[i], kv[i + 1]);
        }
        return row;
    }

    private static final MetaField REASON_DESCRIPTION =
            field("reasonDescription", c("[[\"reason\", \"=\", \"Others\"]]", null, null, null, null));

    @Test
    void onCreateAConditionalRequiredFieldIsDemandedOnlyWhenItsConditionHolds() {
        FieldConstraintsEnforcer e = enforcer(AccessType.CREATE, REASON_DESCRIPTION);
        assertThatThrownBy(() -> e.enforceCreate(row("reason", "Others")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reasonDescription");
        assertThatCode(() -> e.enforceCreate(row("reason", "Relocation"))).doesNotThrowAnyException();
        assertThatCode(() -> e.enforceCreate(row("reason", "Others", "reasonDescription", "moved"))).doesNotThrowAnyException();
    }

    @Test
    void onUpdateTheDocumentedTraceHolds_patchChangesReasonOnly() {
        // stored: {reason: Relocation, reasonDescription: null}; patch: {reason: Others}
        FieldConstraintsEnforcer e = enforcer(AccessType.UPDATE, REASON_DESCRIPTION);
        Map<String, Object> original = row("id", 7L, "reason", "Relocation", "reasonDescription", null);
        Map<String, Object> patch = row("id", 7L, "reason", "Others");
        Map<String, Object> merged = new HashMap<>(original);
        merged.putAll(patch);
        assertThatThrownBy(() -> e.enforceUpdate(merged, patch, original))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reasonDescription");
    }

    @Test
    void onUpdateAnUnrelatedEditDoesNotWakeTheRule() {
        FieldConstraintsEnforcer e = enforcer(AccessType.UPDATE, REASON_DESCRIPTION);
        Map<String, Object> original = row("id", 7L, "reason", "Others", "reasonDescription", null, "status", "Draft");
        Map<String, Object> patch = row("id", 7L, "status", "Approved");
        Map<String, Object> merged = new HashMap<>(original);
        merged.putAll(patch);
        assertThatCode(() -> e.enforceUpdate(merged, patch, original)).doesNotThrowAnyException();
    }

    @Test
    void requiredWhenTrueCannotBeClearedButMayBeOmitted() {
        MetaField costCentre = field("costCentreId", c("true", null, null, null, null));
        FieldConstraintsEnforcer create = enforcer(AccessType.CREATE, costCentre);
        assertThatThrownBy(() -> create.enforceCreate(row("status", "Draft"))).hasMessageContaining("costCentreId");

        FieldConstraintsEnforcer update = enforcer(AccessType.UPDATE, costCentre);
        Map<String, Object> original = row("id", 1L, "costCentreId", null, "status", "Draft");
        Map<String, Object> omit = row("id", 1L, "status", "Active");
        Map<String, Object> mergedOmit = new HashMap<>(original);
        mergedOmit.putAll(omit);
        assertThatCode(() -> update.enforceUpdate(mergedOmit, omit, original)).doesNotThrowAnyException();
        Map<String, Object> clear = row("id", 1L, "costCentreId", null);
        Map<String, Object> mergedClear = new HashMap<>(original);
        mergedClear.putAll(clear);
        assertThatThrownBy(() -> update.enforceUpdate(mergedClear, clear, original)).hasMessageContaining("costCentreId");
    }

    @Test
    void hiddenFieldsAreNotJudged() {
        MetaField lateMinutes = field("lateMinutes",
                c("true", "[[\"checkInStatus\", \"=\", \"Normal\"]]", null, null, null));
        FieldConstraintsEnforcer e = enforcer(AccessType.CREATE, lateMinutes);
        assertThatCode(() -> e.enforceCreate(row("checkInStatus", "Normal"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> e.enforceCreate(row("checkInStatus", "Late"))).hasMessageContaining("lateMinutes");
    }

    @Test
    void readonlyWhenRejectsAnAssignmentNotAnUnchangedValue() {
        MetaField amount = field("amount", c(null, null, "[[\"status\", \"!=\", \"Draft\"]]", null, null));
        FieldConstraintsEnforcer e = enforcer(AccessType.UPDATE, amount);
        Map<String, Object> original = row("id", 1L, "status", "Approved", "amount", "100");
        Map<String, Object> change = row("id", 1L, "amount", 200);
        Map<String, Object> mergedChange = new HashMap<>(original);
        mergedChange.putAll(change);
        assertThatThrownBy(() -> e.enforceUpdate(mergedChange, change, original)).hasMessageContaining("readonly");
        Map<String, Object> same = row("id", 1L, "amount", "100");
        Map<String, Object> mergedSame = new HashMap<>(original);
        mergedSame.putAll(same);
        assertThatCode(() -> e.enforceUpdate(mergedSame, same, original)).doesNotThrowAnyException();
        Map<String, Object> draft = row("id", 1L, "status", "Draft", "amount", 300);
        assertThatCode(() -> e.enforceUpdate(new HashMap<>(draft), draft, row("id", 1L, "status", "Draft", "amount", "1")))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidWhenRejectsWithTheDeclaredMessageAndReadsTheMergedRow() {
        MetaField endDate = field("endDate", c(null, null, null,
                "[[\"endDate\", \"<\", \"{{ @startDate }}\"]]", "End date cannot precede start date."));
        FieldConstraintsEnforcer e = enforcer(AccessType.UPDATE, endDate);
        // the patch moves startDate past the stored endDate — the rule wakes because startDate is referenced
        Map<String, Object> original = row("id", 1L, "startDate", "2026-01-01", "endDate", "2026-06-30");
        Map<String, Object> patch = row("id", 1L, "startDate", LocalDate.of(2026, 7, 1));
        Map<String, Object> merged = new HashMap<>(original);
        merged.putAll(patch);
        assertThatThrownBy(() -> e.enforceUpdate(merged, patch, original))
                .hasMessage("End date cannot precede start date.");
    }

    @Test
    void columnsToReadRegistersBothDirections() {
        io.softa.framework.orm.meta.MetaModel model = new io.softa.framework.orm.meta.MetaModel();
        ReflectionTestUtils.invokeMethod(model, "addConditionalField", REASON_DESCRIPTION);
        assertThat(FieldConstraintsEnforcer.columnsToRead(model, Set.of("reason"), f -> true))
                .containsExactlyInAnyOrder("reason", "reasonDescription");
        assertThat(FieldConstraintsEnforcer.columnsToRead(model, Set.of("reasonDescription"), f -> true))
                .containsExactlyInAnyOrder("reason", "reasonDescription");
        assertThat(FieldConstraintsEnforcer.columnsToRead(model, Set.of("status"), f -> true)).isEmpty();
    }
}
