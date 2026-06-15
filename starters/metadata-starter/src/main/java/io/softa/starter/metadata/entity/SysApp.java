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

    @Field(required = true)
    private String name;

    @Field(required = true)
    private String appCode;

    @Field
    private String appType;

    @Field
    private DatabaseType databaseType;

    @Field(length = 256)
    private String description;
}
