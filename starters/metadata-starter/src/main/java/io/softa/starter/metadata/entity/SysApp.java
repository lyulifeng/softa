package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.DatabaseType;

/**
 * SysApp Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "System App")
public class SysApp extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Name", required = true, length = 64)
    private String name;

    @Field(label = "App Code", required = true, length = 64)
    private String appCode;

    @Field(label = "App Type", length = 64)
    private String appType;

    @Field(label = "Database Type")
    private DatabaseType databaseType;

    @Field(label = "Description", length = 256)
    private String description;
}
