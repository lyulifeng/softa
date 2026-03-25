package io.softa.starter.studio.release.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import io.softa.starter.studio.release.enums.DesignAppVersionType;

/**
 * DesignAppVersionDTO for creating a new version
 */
@Data
@Schema(name = "DesignAppVersionDTO", description = "DesignAppVersionDTO for creating a new version")
public class DesignAppVersionDTO {

    @Schema(description = "App ID")
    private Long appId;

    @Schema(description = "Version Name")
    private String name;

    @Schema(description = "Version Type: Normal or Hotfix")
    private DesignAppVersionType versionType;

    @Schema(description = "Upgrade description")
    private String description;

}
