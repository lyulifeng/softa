package io.softa.framework.web.dto;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "OnChangeParams")
public class OnChangeParams {

    @Schema(description = "Id of the row being edited; absent when creating a new row.")
    private String id;

    @Schema(description = "New value of the changed field, in API shape.")
    private Object value;

    @Schema(description = "Current values of the companion fields the client declared to send along, in API shape.")
    private Map<String, Object> values;
}
