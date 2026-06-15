package io.softa.app.demo.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * DeptInfo Model
 */
@Data
@Model(label = "Department", businessKey = {"code"})
@EqualsAndHashCode(callSuper = true)
public class DeptInfo extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(required = true, length = 100)
    private String name;

    @Field(copyable = false)
    private String code;

    @Field(label = "Employees", fieldType = FieldType.ONE_TO_MANY, relatedField = "deptId")
    private List<EmpInfo> empIds;

    @Field(length = 256)
    private String description;

    @Field
    private Boolean active;
}
