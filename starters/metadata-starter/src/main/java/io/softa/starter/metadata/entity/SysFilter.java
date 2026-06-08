package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * SysFilter Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "System Filter")
public class SysFilter extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Name", required = true, length = 64)
    private String name;

    @Field(label = "Filters", required = true)
    private Filters filters;

    @Field(label = "Model", required = true, length = 64)
    private String model;

    @Field(label = "Query", length = 256)
    private String query;
}
