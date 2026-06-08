package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * UserDefaultView Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "User Default View")
public class UserDefaultView extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "View ID")
    private Long viewId;

    @Field(label = "View Code", required = true, length = 64)
    private String viewCode;

    @Field(label = "Nav ID")
    private Long navId;

    @Field(label = "Model Name", required = true, length = 64)
    private String modelName;
}
