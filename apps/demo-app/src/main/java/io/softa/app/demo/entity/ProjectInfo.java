package io.softa.app.demo.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;

/**
 * ProjectInfo Model
 */
@Data
@Model(label = "Project", businessKey = {"code"})
@EqualsAndHashCode(callSuper = true)
public class ProjectInfo extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(required = true, length = 100)
    private String name;

    @Field(copyable = false)
    private String code;

    @Field(label = "Employees", fieldType = FieldType.MANY_TO_MANY,
            relatedModel = EmpInfo.class, joinModel = EmpProjectRel.class,
            joinLeft = "projectId", joinRight = "empId")
    private List<Long> empIds;

    @Field(length = 256)
    private String description;

    @Field
    private Boolean active;
}
