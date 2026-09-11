package io.softa.framework.orm.service.validation;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.enums.AccessType;

/**
 * Runs the {@link ModelWriteValidator}s that support a model, in {@code @Order}, at the write roots.
 *
 * <p>Collected through {@link ObjectProvider#orderedStream()}, which is the same ordering Spring gives
 * an injected {@code List} — the leave validator chain relies on it already. A validator without
 * {@code @Order} still runs, last and in an order Spring does not promise; that is logged once at
 * boot because the bands in {@link ModelWriteValidator} only mean something when everyone declares
 * one.
 */
@Slf4j
@Component
public class ModelWriteValidatorChain {

    private final List<ModelWriteValidator> validators;

    public ModelWriteValidatorChain(ObjectProvider<ModelWriteValidator> provider) {
        this.validators = provider.orderedStream().toList();
        for (ModelWriteValidator validator : validators) {
            boolean ordered = validator instanceof Ordered
                    || AnnotationUtils.findAnnotation(validator.getClass(), Order.class) != null;
            if (!ordered) {
                log.warn("ModelWriteValidator {} declares no @Order; it runs after the ordered ones,"
                        + " in an order Spring does not promise.", validator.getClass().getName());
            }
        }
        if (!validators.isEmpty()) {
            log.info("Registered {} ModelWriteValidator(s): {}", validators.size(),
                    validators.stream().map(v -> v.getClass().getSimpleName()).toList());
        }
    }

    /** Whether any validator applies to the model — lets the caller skip fetching originals. */
    public boolean supports(String modelName) {
        return validators.stream().anyMatch(v -> v.supports(modelName));
    }

    /** Create: batch first, then each row, per validator; throws once with everything rejected. */
    public void validateCreate(String modelName, List<Map<String, Object>> rows) {
        List<ModelWriteValidator> applicable = applicable(modelName);
        if (applicable.isEmpty()) {
            return;
        }
        WriteValidationErrors errors = new WriteValidationErrors();
        for (ModelWriteValidator validator : applicable) {
            validator.validateBatch(modelName, rows, AccessType.CREATE);
            for (int i = 0; i < rows.size(); i++) {
                validator.validateCreate(new WriteContext(modelName, AccessType.CREATE, i, rows.get(i), null, errors));
            }
        }
        throwIfRejected(errors);
    }

    /**
     * Update: each patch is merged onto its stored row before the validators see it.
     *
     * @param patches the request rows (must carry the id)
     * @param originalsById the stored rows, keyed by id; a patch whose row is missing is skipped here
     *                      (the write itself will report it)
     */
    public void validateUpdate(String modelName, List<Map<String, Object>> patches,
                               Map<Serializable, Map<String, Object>> originalsById) {
        List<ModelWriteValidator> applicable = applicable(modelName);
        if (applicable.isEmpty()) {
            return;
        }
        WriteValidationErrors errors = new WriteValidationErrors();
        for (ModelWriteValidator validator : applicable) {
            validator.validateBatch(modelName, patches, AccessType.UPDATE);
            for (int i = 0; i < patches.size(); i++) {
                Map<String, Object> patch = patches.get(i);
                Map<String, Object> original = originalsById.get((Serializable) patch.get(ModelConstant.ID));
                if (original == null) {
                    continue;
                }
                Map<String, Object> merged = new java.util.HashMap<>(original);
                merged.putAll(patch);
                validator.validateUpdate(new WriteContext(modelName, AccessType.UPDATE, i, merged, original, errors));
            }
        }
        throwIfRejected(errors);
    }

    /** Delete: every applicable validator sees the ids. */
    public void validateDelete(String modelName, List<? extends Serializable> ids) {
        for (ModelWriteValidator validator : applicable(modelName)) {
            validator.validateDelete(modelName, ids);
        }
    }

    private List<ModelWriteValidator> applicable(String modelName) {
        return validators.stream().filter(v -> v.supports(modelName)).toList();
    }

    private static void throwIfRejected(WriteValidationErrors errors) {
        if (errors.hasErrors()) {
            throw new WriteValidationException(errors.errors());
        }
    }
}
