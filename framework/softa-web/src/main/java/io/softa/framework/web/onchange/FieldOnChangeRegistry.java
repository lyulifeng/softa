package io.softa.framework.web.onchange;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.web.dto.OnChangeParams;
import io.softa.framework.web.dto.OnChangeResponse;

/**
 * Collects the {@link FieldOnChangeHandler} beans at startup and dispatches
 * {@code /{modelName}/onChange/{fieldName}} requests to the handler registered for the
 * (model, field) pair.
 */
@Component
public class FieldOnChangeRegistry {

    private final Map<String, FieldOnChangeHandler> handlers = new HashMap<>();

    public FieldOnChangeRegistry(ObjectProvider<FieldOnChangeHandler> handlerProvider) {
        handlerProvider.orderedStream().forEach(this::register);
    }

    private void register(FieldOnChangeHandler handler) {
        if (handler.model() == null || handler.model().isBlank()
                || handler.fields() == null || handler.fields().isEmpty()) {
            throw new IllegalStateException("onChange handler " + handler.getClass().getName()
                    + " must declare a model and at least one trigger field");
        }
        for (String fieldName : handler.fields()) {
            FieldOnChangeHandler previous = handlers.put(handlerKey(handler.model(), fieldName), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate onChange handlers registered for "
                        + handler.model() + "." + fieldName + ": "
                        + previous.getClass().getName() + " and " + handler.getClass().getName());
            }
        }
    }

    /**
     * Dispatch an onChange request to the handler registered for the (model, field) pair.
     *
     * @param modelName model name
     * @param fieldName changed field name
     * @param onChangeParams onChange params
     * @return the handler's response; never null
     */
    public OnChangeResponse dispatch(String modelName, String fieldName, OnChangeParams onChangeParams) {
        ModelManager.validateModelField(modelName, fieldName);
        FieldOnChangeHandler handler = handlers.get(handlerKey(modelName, fieldName));
        if (handler == null) {
            throw new BusinessException("No onChange handler is registered for {0}.{1}", modelName, fieldName);
        }
        OnChangeResponse response = handler.onChange(new OnChangeContext(modelName, fieldName,
                onChangeParams.getId(), onChangeParams.getValue(), onChangeParams.getValues()));
        if (response == null) {
            return new OnChangeResponse();
        }
        validateResponseFields(modelName, response);
        return response;
    }

    private void validateResponseFields(String modelName, OnChangeResponse response) {
        Set<String> responseFields = new LinkedHashSet<>();
        if (response.getValues() != null) {
            responseFields.addAll(response.getValues().keySet());
        }
        if (response.getReadonly() != null) {
            responseFields.addAll(response.getReadonly());
        }
        if (response.getRequired() != null) {
            responseFields.addAll(response.getRequired());
        }
        if (!responseFields.isEmpty()) {
            ModelManager.validateModelFields(modelName, responseFields);
        }
    }

    private static String handlerKey(String modelName, String fieldName) {
        return modelName + ":" + fieldName;
    }
}
