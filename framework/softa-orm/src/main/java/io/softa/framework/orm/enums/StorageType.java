package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Storage type: RDBMS, ES, Doris
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum StorageType {
    @OptionItem(label = "Relational Database Management System")
    RDBMS("RDBMS"),
    @OptionItem(label = "ElasticSearch")
    ES("ES"),
    @OptionItem(label = "Doris OLAP")
    DORIS("Doris");

    @JsonValue
    private final String type;
}
