package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Database type for {@code SysApp.databaseType} and external data sources.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Database Type")
public enum DatabaseType {
    @OptionItem(label = "MySQL")
    MYSQL("MySQL"),
    @OptionItem(label = "PostgreSQL")
    POSTGRESQL("PostgreSQL"),
    @OptionItem(label = "Oracle")
    ORACLE("Oracle"),
    @OptionItem(label = "SQLServer")
    SQLSERVER("SQLServer"),
    @OptionItem(label = "TiDB")
    TIDB("TiDB"),
    @OptionItem(label = "ElasticSearch")
    ELASTICSEARCH("ElasticSearch"),
    @OptionItem(label = "MongoDB")
    MONGODB("MongoDB"),
    @OptionItem(label = "Redis")
    REDIS("Redis");

    @JsonValue
    private final String type;
}
