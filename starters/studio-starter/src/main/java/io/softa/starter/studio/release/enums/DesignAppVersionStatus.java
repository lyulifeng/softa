package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet
public enum DesignAppVersionStatus {
    DRAFT("Draft"),
    SEALED("Sealed"),
    FROZEN("Frozen"),
    ;

    @JsonValue
    private final String status;
}
