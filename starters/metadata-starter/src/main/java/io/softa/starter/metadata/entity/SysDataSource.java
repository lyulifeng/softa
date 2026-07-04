package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * SysDataSource Model
 * The default datasource is configured in the spring.datasource configuration.
 * This model is used to configure additional external data sources.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "External Data Source",
        description = "Configure additional external data sources (the default is in spring.datasource)."
)
public class SysDataSource extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(required = true, length = 32)
    private String name;

    @Field(label = "Data Source Key", length = 32)
    private String dsKey;

    @Field(label = "JDBC URL", required = true, length = 256)
    private String jdbcUrl;

    @Field(required = true)
    private String username;

    @Field(required = true, length = 256)
    private String password;

    @Field(required = true)
    private Integer initialSize;

    @Field(required = true)
    private Integer maxActive;

    @Field(required = true)
    private Integer minIdle;

    @Field(required = true)
    private Integer maxWait;

    @Field
    private Boolean readonly;

    @Field(length = 256)
    private String description;
}
