package io.softa.framework.web.onchange;

import java.util.Set;

import io.softa.framework.web.dto.OnChangeResponse;

/**
 * SPI behind the {@code POST /{modelName}/onChange/{fieldName}} endpoint.
 *
 * <p>Implementations are Spring beans collected by {@link FieldOnChangeRegistry} at startup.
 * A handler serves one model and one or more trigger fields of that model; registering two
 * handlers for the same (model, field) pair fails the boot.
 *
 * <p>Contract:
 * <ul>
 * <li>onChange is an advisory, read-only computation — implementations must not persist
 * changes.</li>
 * <li>{@code values} in the response patches only the returned keys; a null value clears the
 * field on the client.</li>
 * <li>{@code readonly} / {@code required} are complete field-name lists, not patches: each list
 * names every field in that state for the current trigger value, and a governed field left out
 * of a list is reset on the client. Return an empty list (or leave it null) to lift the rules
 * this handler set earlier.</li>
 * <li>Every field named in the response must exist on the model; unknown names are rejected by
 * the registry.</li>
 * </ul>
 */
public interface FieldOnChangeHandler {

    /**
     * Model name this handler serves, e.g. {@code "OvertimeRequest"}.
     *
     * @return model name
     */
    String model();

    /**
     * Trigger field names of {@link #model()} this handler serves.
     *
     * @return trigger field names
     */
    Set<String> fields();

    /**
     * Compute the linkage response for a trigger field change.
     *
     * @param context onChange context
     * @return linkage response; a null result is treated as an empty response
     */
    OnChangeResponse onChange(OnChangeContext context);
}
