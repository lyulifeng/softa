package io.softa.starter.metadata.sequence.service;

import java.util.List;
import java.util.Map;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.starter.metadata.sequence.enums.SequenceMode;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Enforces the v1 invariants on every {@code SysSequence} mutation that
 * goes through {@code ModelService}, regardless of the entry point
 * (admin REST, {@code SysPreDataService.loadPreTenantData}, internal
 * EntityService calls). Throws
 * {@link io.softa.framework.base.exception.IllegalArgumentException}
 * (HTTP 400) on any violation.
 *
 * <p>v1 hard rules:
 * <ul>
 *   <li>{@code code} must match {@code <ModelName>.<fieldName>} convention
 *       (custom codes are out of scope until v1.x)</li>
 *   <li>The model and field must exist in {@code sys_field}</li>
 *   <li>The bound field must not also have a {@code defaultValue}
 *       configured — sequence and static default are mutually exclusive</li>
 *   <li>{@code incrementStep} must be 1</li>
 *   <li>{@code mode} must be NO_GAP or ALLOW_GAP if provided</li>
 *   <li>On update, neither {@code code} nor {@code status} may change</li>
 * </ul>
 */
@Aspect
@Component
public class SysSequenceValidator {

    private static final String MODEL_NAME = "SysSequence";

    @Before("execution(* io.softa.framework.orm.service.ModelService.createOne(..)) " +
            "&& args(modelName, row)")
    public void onCreateOne(String modelName, Map<String, Object> row) {
        if (!MODEL_NAME.equals(modelName)) return;
        validateForCreate(row);
    }

    @Before("execution(* io.softa.framework.orm.service.ModelService.createList(..)) " +
            "&& args(modelName, rows)")
    public void onCreateList(String modelName, List<Map<String, Object>> rows) {
        if (!MODEL_NAME.equals(modelName) || rows == null) return;
        for (Map<String, Object> row : rows) {
            validateForCreate(row);
        }
    }

    @Before("execution(* io.softa.framework.orm.service.ModelService.updateOne(..)) " +
            "&& args(modelName, row)")
    public void onUpdateOne(String modelName, Map<String, Object> row) {
        if (!MODEL_NAME.equals(modelName)) return;
        validateForUpdate(row);
    }

    private void validateForCreate(Map<String, Object> row) {
        String code = stringOf(row.get("code"));
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("SysSequence.code must not be blank");
        }
        validateCodeConvention(code);
        validateNoDefaultValueConflict(code);
        validateIncrementStep(row);
        validateMode(row);
    }

    private void validateForUpdate(Map<String, Object> row) {
        // Reject changes to immutable fields. We can only see what the caller is trying
        // to write; if these keys appear at all we treat them as a change attempt.
        if (row.containsKey("code")) {
            throw new IllegalArgumentException(
                    "SysSequence.code is immutable; create/replace via release process instead");
        }
        if (row.containsKey("status")) {
            throw new IllegalArgumentException(
                    "SysSequence.status cannot be changed via API in v1 (use DBA channel for emergencies)");
        }
        validateIncrementStep(row);
        validateMode(row);
    }

    /** Convention check: code must look like {@code Foo.bar} and resolve in metadata. */
    private void validateCodeConvention(String code) {
        int dot = code.indexOf('.');
        if (dot <= 0 || dot >= code.length() - 1) {
            throw new IllegalArgumentException(
                    "SysSequence.code must follow the \"<ModelName>.<fieldName>\" convention: " + code);
        }
        String model = code.substring(0, dot);
        String field = code.substring(dot + 1);
        MetaField mf = ModelManager.getModelFieldOrNull(model, field);
        if (mf == null) {
            throw new IllegalArgumentException(
                    "SysSequence.code references unknown field " + model + "." + field);
        }
    }

    private void validateNoDefaultValueConflict(String code) {
        int dot = code.indexOf('.');
        if (dot <= 0) return;
        MetaField mf = ModelManager.getModelFieldOrNull(code.substring(0, dot), code.substring(dot + 1));
        if (mf == null) return;
        String def = mf.getDefaultValue();
        if (def != null && !def.isBlank()) {
            throw new IllegalArgumentException(
                    "Field " + code + " already has a defaultValue; auto-fill via sequence and "
                            + "static defaultValue are mutually exclusive");
        }
    }

    private void validateIncrementStep(Map<String, Object> row) {
        Object step = row.get("incrementStep");
        if (step == null) return;
        int s = (step instanceof Number n) ? n.intValue() : Integer.parseInt(step.toString());
        if (s != 1) {
            throw new IllegalArgumentException("SysSequence.incrementStep must be 1 in v1; got " + s);
        }
    }

    private void validateMode(Map<String, Object> row) {
        Object mode = row.get("mode");
        if (mode == null) return;
        String name = mode.toString();
        try {
            SequenceMode.valueOf(name);
        } catch (java.lang.IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "SysSequence.mode must be NO_GAP or ALLOW_GAP; got " + name);
        }
    }

    private static String stringOf(Object v) {
        return v == null ? null : v.toString();
    }
}
