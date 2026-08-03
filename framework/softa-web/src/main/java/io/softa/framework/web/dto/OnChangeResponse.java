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
    private Map<String, Object> values;

    private List<String> readonly;

    private List<String> required;

    private List<String> hidden;
}
