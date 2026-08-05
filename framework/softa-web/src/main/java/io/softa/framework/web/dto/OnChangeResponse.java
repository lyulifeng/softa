package io.softa.framework.web.dto;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "OnChangeResponse")
public class OnChangeResponse {

    @Schema(description = "Field values to write back; only the returned keys are patched, and a null value clears the field.")
    private Map<String, Object> values;

    @Schema(description = "Complete list of the fields that are readonly for the current trigger value; a governed field left out of the list is reset on the client.")
    private List<String> readonly;

    @Schema(description = "Complete list of the fields that are required for the current trigger value; a governed field left out of the list is reset on the client.")
    private List<String> required;
}
