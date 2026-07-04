package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet
public enum FileSource {
    DOWNLOAD("Download"),
    UPLOAD("Upload"),
    @OptionItem(label = "URL")
    URL("URL"),
            ;

    @JsonValue
    private final String code;
}
