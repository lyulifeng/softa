package io.softa.framework.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request of the `fixUnencryptedData` toolkit operation: which field to encrypt, and how to run it.
 */
@Schema(name = "FixUnencryptedDataParams")
@Data
public class FixUnencryptedDataParams {

    @Schema(description = "Model name")
    @NotBlank(message = "The model name cannot be empty!")
    private String model;

    @Schema(description = "Field name")
    @NotBlank(message = "The field name cannot be empty!")
    private String field;

    @Schema(description = "Report the rows that would be changed, without writing anything")
    private boolean dryRun;

}
