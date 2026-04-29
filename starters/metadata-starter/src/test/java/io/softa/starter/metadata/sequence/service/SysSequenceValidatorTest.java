package io.softa.starter.metadata.sequence.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link SysSequenceValidator}. Covers the deterministic
 * reject paths against arbitrary input maps; integration coverage of the
 * AOP wiring (real {@code ModelService} interception) lives in the
 * integration test suite.
 */
class SysSequenceValidatorTest {

    private SysSequenceValidator validator;
    private MockedStatic<ModelManager> modelManagerStub;

    @BeforeEach
    void setUp() {
        validator = new SysSequenceValidator();
        modelManagerStub = mockStatic(ModelManager.class);
        // Default: lookup returns a vanilla MetaField (existence check passes).
        modelManagerStub.when(() -> ModelManager.getModelFieldOrNull("Employee", "code"))
                .thenReturn(metaField(null));
    }

    @AfterEach
    void tearDown() {
        modelManagerStub.close();
    }

    // -------- create-path validations --------

    @Test
    void createOne_blankCode_rejected() {
        assertThatThrownBy(() -> validator.onCreateOne("SysSequence", row(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void createOne_codeWithoutDot_rejected() {
        assertThatThrownBy(() -> validator.onCreateOne("SysSequence", row("PAYROLL_RUN", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ModelName.fieldName");
    }

    @Test
    void createOne_codeReferencesUnknownField_rejected() {
        modelManagerStub.when(() -> ModelManager.getModelFieldOrNull("Foo", "bar"))
                .thenReturn(null);
        assertThatThrownBy(() -> validator.onCreateOne("SysSequence", row("Foo.bar", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field");
    }

    @Test
    void createOne_fieldHasDefaultValue_rejected() {
        modelManagerStub.when(() -> ModelManager.getModelFieldOrNull("Employee", "code"))
                .thenReturn(metaField("EMP-LEGACY"));
        assertThatThrownBy(() -> validator.onCreateOne("SysSequence", row("Employee.code", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultValue");
    }

    @Test
    void createOne_validRow_passes() {
        Map<String, Object> row = row("Employee.code", null);
        row.put("incrementStep", 1);
        row.put("mode", "NO_GAP");
        assertThatCode(() -> validator.onCreateOne("SysSequence", row)).doesNotThrowAnyException();
    }

    @Test
    void createOne_otherModel_skipped() {
        // Validator only intercepts SysSequence; any other modelName must pass through untouched
        // even with garbage payload.
        assertThatCode(() -> validator.onCreateOne("Employee", row(null, null)))
                .doesNotThrowAnyException();
    }

    // -------- update-path validations --------

    @Test
    void updateOne_changingCode_rejected() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("code", "Employee.staffNo");
        assertThatThrownBy(() -> validator.onUpdateOne("SysSequence", row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void updateOne_changingStatus_rejected() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("status", "Disabled");
        assertThatThrownBy(() -> validator.onUpdateOne("SysSequence", row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void updateOne_validUpdate_passes() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("template", "EMP-{yyyy}-{seq:5}");
        row.put("incrementStep", 1);
        assertThatCode(() -> validator.onUpdateOne("SysSequence", row)).doesNotThrowAnyException();
    }

    // -------- shared validations on both paths --------

    @Test
    void incrementStepOtherThanOne_rejected() {
        Map<String, Object> row = row("Employee.code", null);
        row.put("incrementStep", 5);
        assertThatThrownBy(() -> validator.onCreateOne("SysSequence", row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incrementStep must be 1");
    }

    @Test
    void unknownMode_rejected() {
        Map<String, Object> row = row("Employee.code", null);
        row.put("mode", "FAST_GAP");
        assertThatThrownBy(() -> validator.onCreateOne("SysSequence", row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode must be");
    }

    // -------- bulk createList path --------

    @Test
    void createList_propagatesRejection() {
        Map<String, Object> good = row("Employee.code", null);
        Map<String, Object> bad = row("PAYROLL_RUN", null);
        assertThatThrownBy(() -> validator.onCreateList("SysSequence", List.of(good, bad)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ModelName.fieldName");
    }

    // -------- helpers --------

    private static Map<String, Object> row(String code, String defaultValue) {
        Map<String, Object> r = new HashMap<>();
        r.put("code", code);
        return r;
    }

    private static MetaField metaField(String defaultValue) {
        // Lombok @Setter is package-private; use reflection-friendly construction
        // through the ORM API. For unit tests we just need a concrete instance.
        MetaField mf = new MetaField();
        // defaultValue is package-protected setter; use the framework getter convention.
        // Easiest path: set via reflection or accept null in tests.
        if (defaultValue != null) {
            try {
                java.lang.reflect.Field f = MetaField.class.getDeclaredField("defaultValue");
                f.setAccessible(true);
                f.set(mf, defaultValue);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return mf;
    }
}
