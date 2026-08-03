package io.softa.framework.web.onchange;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.web.dto.OnChangeParams;
import io.softa.framework.web.dto.OnChangeResponse;

class FieldOnChangeRegistryTest {

    private static FieldOnChangeHandler handler(String model, Set<String> fields,
                                                Function<OnChangeContext, OnChangeResponse> body) {
        return new FieldOnChangeHandler() {
            @Override
            public String model() {
                return model;
            }

            @Override
            public Set<String> fields() {
                return fields;
            }

            @Override
            public OnChangeResponse onChange(OnChangeContext context) {
                return body.apply(context);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static FieldOnChangeRegistry registryOf(FieldOnChangeHandler... handlers) {
        ObjectProvider<FieldOnChangeHandler> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.orderedStream()).thenReturn(Stream.of(handlers));
        return new FieldOnChangeRegistry(provider);
    }

    private static OnChangeParams params(String id, Object value, Map<String, Object> values) {
        OnChangeParams params = new OnChangeParams();
        params.setId(id);
        params.setValue(value);
        params.setValues(values);
        return params;
    }

    @Test
    void dispatchRoutesToTheHandlerOfTheModelAndFieldAndPassesTheContext() {
        FieldOnChangeRegistry registry = registryOf(
                handler("OvertimeRequest", Set.of("employeeId", "overtimeDate"), context ->
                        OnChangeResponse.builder()
                                .values(Map.of("compensationType", context.value() + "@" + context.values().get("overtimeDate")))
                                .readonly(List.of("compensationType"))
                                .build()),
                handler("LeaveRequest", Set.of("leaveType"), context -> new OnChangeResponse()));

        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            OnChangeResponse response = registry.dispatch("OvertimeRequest", "employeeId",
                    params("700001", "600001", Map.of("overtimeDate", "2026-01-05")));

            Assertions.assertEquals(Map.of("compensationType", "600001@2026-01-05"), response.getValues());
            Assertions.assertEquals(List.of("compensationType"), response.getReadonly());
            modelManager.verify(() -> ModelManager.validateModelField("OvertimeRequest", "employeeId"));
        }
    }

    @Test
    void dispatchFailsWhenNoHandlerIsRegisteredForTheField() {
        FieldOnChangeRegistry registry = registryOf(
                handler("OvertimeRequest", Set.of("employeeId"), context -> new OnChangeResponse()));

        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            BusinessException exception = Assertions.assertThrows(BusinessException.class,
                    () -> registry.dispatch("OvertimeRequest", "overtimeDate", params(null, "2026-01-05", null)));

            Assertions.assertTrue(exception.getMessage().contains("OvertimeRequest"));
            Assertions.assertTrue(exception.getMessage().contains("overtimeDate"));
        }
    }

    @Test
    void duplicateHandlersForTheSameModelAndFieldFailTheBoot() {
        FieldOnChangeHandler first = handler("OvertimeRequest", Set.of("employeeId"), context -> null);
        FieldOnChangeHandler second = handler("OvertimeRequest", Set.of("employeeId"), context -> null);

        Assertions.assertThrows(IllegalStateException.class, () -> registryOf(first, second));
    }

    @Test
    void handlerWithoutModelOrTriggerFieldsFailsTheBoot() {
        Assertions.assertThrows(IllegalStateException.class,
                () -> registryOf(handler(" ", Set.of("employeeId"), context -> null)));
        Assertions.assertThrows(IllegalStateException.class,
                () -> registryOf(handler("OvertimeRequest", Set.of(), context -> null)));
    }

    @Test
    void nullHandlerResponseBecomesAnEmptyResponse() {
        FieldOnChangeRegistry registry = registryOf(
                handler("OvertimeRequest", Set.of("employeeId"), context -> null));

        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            OnChangeResponse response = registry.dispatch("OvertimeRequest", "employeeId",
                    params(null, "600001", null));

            Assertions.assertNull(response.getValues());
            Assertions.assertNull(response.getReadonly());
            Assertions.assertNull(response.getRequired());
        }
    }

    @Test
    void responseNamingAFieldUnknownToTheModelIsRejected() {
        FieldOnChangeRegistry registry = registryOf(
                handler("OvertimeRequest", Set.of("employeeId"), context ->
                        OnChangeResponse.builder().readonly(List.of("nope")).build()));

        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            modelManager.when(() -> ModelManager.validateModelFields(Mockito.eq("OvertimeRequest"), Mockito.anyCollection()))
                    .thenThrow(new BusinessException("Field nope does not exist in model OvertimeRequest"));

            Assertions.assertThrows(BusinessException.class,
                    () -> registry.dispatch("OvertimeRequest", "employeeId", params(null, "600001", null)));
        }
    }

    @Test
    void contextDefaultsMissingCompanionValuesToAnEmptyMap() {
        OnChangeContext context = new OnChangeContext("OvertimeRequest", "employeeId", null, "600001", null);

        Assertions.assertEquals(Map.of(), context.values());
    }
}
