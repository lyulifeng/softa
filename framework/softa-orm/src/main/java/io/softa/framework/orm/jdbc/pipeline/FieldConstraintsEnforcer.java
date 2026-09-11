package io.softa.framework.orm.jdbc.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.domain.EvalContext;
import io.softa.framework.orm.domain.FilterEvaluator;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.FieldCondition;
import io.softa.framework.orm.meta.FieldConstraints;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;

/**
 * Applies the conditional field constraints — {@code requiredWhen} / {@code hiddenWhen} /
 * {@code readonlyWhen} / {@code invalidWhen} — to the rows of one write.
 *
 * <p>Runs <b>before</b> the field-processor chain, on the raw values: on create the request row, on
 * update the patch merged onto the stored row. The chain converts field by field, so a neighbour a
 * condition reads might or might not have been coerced yet depending on declaration order; reading
 * the values as they arrived gives create and update — and the frontend, which reads the form — the
 * same picture. The value domain ({@code min} / {@code max} / {@code pattern}) is the opposite case
 * and stays in the processors: it needs the coerced value.
 *
 * <p>Rules, written down because the frontend evaluator must give the same answers:
 * <ol>
 *   <li><b>Hidden fields are not checked.</b> {@code hidden} or a matching {@code hiddenWhen} skips the
 *       field's required / readonly / invalid rules — what the form does not show it cannot demand,
 *       otherwise a record with a hidden required field could never be saved.</li>
 *   <li><b>On update, a field is evaluated only when the patch touches it</b> — the field itself, or a
 *       field one of its conditions reads. A row whose {@code reasonDescription} is legitimately empty
 *       is not rejected by an unrelated edit; changing {@code reason} to {@code Others} is.
 *       {@code requiredWhen = true} reads nothing, so it fires on create and when the field is sent
 *       (clearing it is rejected; not sending it is not) — the "cannot clear, may omit" contract.</li>
 *   <li><b>{@code readonlyWhen} rejects an assignment</b>, not a value: the patch names the field and the
 *       value differs from what is stored (on create: is not null).</li>
 *   <li><b>{@code invalidWhen} rejects with the declared message</b>; without one, a generated sentence
 *       that names the field.</li>
 * </ol>
 * The static {@code required} check stays in the processors — a {@code NOT NULL} column cannot be
 * skipped by hiding the field, the database would reject the row anyway.
 */
public final class FieldConstraintsEnforcer {

    private final String modelName;
    private final AccessType accessType;
    private final List<MetaField> conditionalFields;
    private final Function<String, @Nullable FieldType> typeOf;
    private final EvalContext ctx;

    /**
     * @param modelName the model being written
     * @param accessType CREATE or UPDATE
     * @param conditionalFields the fields carrying conditions ({@link MetaModel#getConditionalFields()})
     * @param typeOf the type of a field of the model, or null when unknown; drives value coercion
     * @param ctx reserved variables and environment tokens for this write
     */
    public FieldConstraintsEnforcer(String modelName, AccessType accessType, List<MetaField> conditionalFields,
                                    Function<String, @Nullable FieldType> typeOf, EvalContext ctx) {
        this.modelName = modelName;
        this.accessType = accessType;
        this.conditionalFields = conditionalFields;
        this.typeOf = typeOf;
        this.ctx = ctx;
    }

    /**
     * The enforcer for a live write, reading the model from {@link ModelManager}; null when the model
     * declares no conditions, so the pipelines pay nothing for the common case.
     */
    public static @Nullable FieldConstraintsEnforcer forModel(String modelName, AccessType accessType) {
        List<MetaField> conditional = ModelManager.getModel(modelName).getConditionalFields();
        if (conditional.isEmpty()) {
            return null;
        }
        return new FieldConstraintsEnforcer(modelName, accessType, conditional, field -> {
            MetaField metaField = ModelManager.getModelFieldOrNull(modelName, field);
            return metaField == null ? null : metaField.getFieldType();
        }, EvalContext.of(accessType));
    }

    /**
     * The stored columns an update must fetch so the conditions can be evaluated: for every
     * conditional field the patch touches (itself or a field it reads), the field and everything it
     * reads. Walks {@link FieldConstraints#referencedFields()} — the same registration the computed
     * fields do for their dependencies.
     *
     * @param model the model
     * @param patchFields the fields the update carries
     * @param isStored whether a field of the model is a stored column
     */
    public static Set<String> columnsToRead(MetaModel model, Set<String> patchFields, Function<String, Boolean> isStored) {
        Set<String> columns = new java.util.HashSet<>();
        for (MetaField field : model.getConditionalFields()) {
            Set<String> refs = field.getConstraints().referencedFields();
            boolean touched = patchFields.contains(field.getFieldName())
                    || refs.stream().anyMatch(patchFields::contains);
            if (!touched) {
                continue;
            }
            if (isStored.apply(field.getFieldName())) {
                columns.add(field.getFieldName());
            }
            refs.stream().filter(isStored::apply).forEach(columns::add);
        }
        return columns;
    }

    /** Create: every conditional field is evaluated against the request row. */
    public void enforceCreate(Map<String, Object> row) {
        for (MetaField field : conditionalFields) {
            enforce(field, row, row, null);
        }
    }

    /**
     * Update: a conditional field is evaluated when the patch touches it or a field it reads.
     *
     * @param mergedRow the patch merged onto the stored row — what the row will be after the write
     * @param patch the fields the request actually sent
     * @param originalRow the stored row (the columns that were fetched), for the readonly comparison
     */
    public void enforceUpdate(Map<String, Object> mergedRow, Map<String, Object> patch, @Nullable Map<String, Object> originalRow) {
        for (MetaField field : conditionalFields) {
            Set<String> refs = field.getConstraints().referencedFields();
            boolean touched = patch.containsKey(field.getFieldName())
                    || refs.stream().anyMatch(patch::containsKey);
            if (touched) {
                enforce(field, mergedRow, patch, originalRow);
            }
        }
    }

    private void enforce(MetaField field, Map<String, Object> row, Map<String, Object> patch,
                         @Nullable Map<String, Object> originalRow) {
        FieldConstraints c = field.getConstraints();
        String name = field.getFieldName();
        if (field.isHidden() || matches(c.hiddenWhen(), row)) {
            return;
        }
        Object value = row.get(name);
        if (c.readonlyWhen() != null && patch.containsKey(name) && matches(c.readonlyWhen(), row)
                && assigned(value, originalRow == null ? null : originalRow.get(name))) {
            throw new IllegalArgumentException(
                    "Model field {0}:{1} is readonly in its current state and cannot be assigned!", modelName, name);
        }
        if (requiredNow(c.requiredWhen(), row) && FilterEvaluator.isBlank(value)) {
            throw new IllegalArgumentException(
                    "Model field {0}:{1} is required and cannot be empty!", modelName, name);
        }
        if (c.invalidWhen() != null && matches(c.invalidWhen(), row)) {
            String message = c.message() != null
                    ? c.message()
                    : "Model field {0}:{1} is not valid: the value {2} does not satisfy the field''s rule.";
            throw new IllegalArgumentException(message, modelName, name, String.valueOf(value));
        }
    }

    private boolean requiredNow(@Nullable FieldCondition condition, Map<String, Object> row) {
        if (condition == null) {
            return false;
        }
        return condition.isAlways() || matches(condition.getFilters(), row);
    }

    private boolean matches(@Nullable Filters condition, Map<String, Object> row) {
        return condition != null && FilterEvaluator.matches(condition, row, ctx, typeOf);
    }

    /** Whether the patch changes the field: a new non-null value on create, a different value on update. */
    private boolean assigned(@Nullable Object value, @Nullable Object original) {
        if (AccessType.CREATE.equals(accessType)) {
            return value != null;
        }
        if (Objects.equals(value, original)) {
            return false;
        }
        // A stored date arrives as text, the patch as LocalDate — compare the text forms before
        // calling it a change.
        return !FilterEvaluator.isBlank(value) || !FilterEvaluator.isBlank(original)
                ? !Objects.equals(String.valueOf(value), String.valueOf(original))
                : false;
    }
}
