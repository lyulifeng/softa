package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet
public enum DesignPortfolioStatus {
    ACTIVE("Active"),
    ARCHIVED("Archived"),
    ;

    @JsonValue
    private final String status;
}
