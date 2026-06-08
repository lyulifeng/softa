package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Design Portfolio Status")
public enum DesignPortfolioStatus {
    @OptionItem(label = "Active")
    ACTIVE("Active"),
    @OptionItem(label = "Archived")
    ARCHIVED("Archived"),
    ;

    @JsonValue
    private final String status;
}
