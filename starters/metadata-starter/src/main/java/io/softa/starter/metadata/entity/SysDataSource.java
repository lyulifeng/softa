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

    @Field(label = "Name", required = true, length = 32)
    private String name;

    @Field(label = "Data Source Key", length = 32)
    private String dsKey;

    @Field(label = "JDBC URL", required = true, length = 256)
    private String jdbcUrl;

    @Field(label = "Username", required = true, length = 64)
    private String username;

    @Field(label = "Password", required = true, length = 256)
    private String password;

    @Field(label = "Initial Size", required = true)
    private Integer initialSize;

    @Field(label = "Max Active", required = true)
    private Integer maxActive;

    @Field(label = "Min Idle", required = true)
    private Integer minIdle;

    @Field(label = "Max Wait", required = true)
    private Integer maxWait;

    @Field(label = "Readonly")
    private Boolean readonly;

    @Field(label = "Description", length = 256)
    private String description;
}
