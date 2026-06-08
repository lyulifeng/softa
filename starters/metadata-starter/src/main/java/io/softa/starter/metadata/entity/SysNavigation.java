package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * SysNavigation Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "System Navigation")
public class SysNavigation extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Name", required = true, length = 64)
    private String name;

    @Field(label = "Type", required = true, length = 64)
    private String type;

    @Field(label = "Code", required = true, length = 64)
    private String code;

    @Field(label = "Model Name", length = 256)
    private String modelName;

    @Field(label = "Parent ID")
    private Long parentId;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Filter", length = 256)
    private String filter;
}
