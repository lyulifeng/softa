package io.softa.framework.web.onchange;

import java.util.Map;

/**
 * Context of one {@code /{modelName}/onChange/{fieldName}} invocation.
 *
 * @param modelName model whose field changed
 * @param fieldName name of the changed field
 * @param id        id of the row being edited; null when creating a new row
 * @param value     new value of the changed field, in API shape
 * @param values    current values of the companion fields the client declared to send along,
 *                  in API shape; never null
 */
public record OnChangeContext(String modelName, String fieldName, String id, Object value,
                              Map<String, Object> values) {

    public OnChangeContext {
        values = values == null ? Map.of() : values;
    }
}
