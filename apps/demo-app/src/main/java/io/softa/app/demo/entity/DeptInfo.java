package io.softa.app.demo.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
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

    @Field(label = "Name", required = true, length = 100)
    private String name;

    @Field(label = "Code", length = 64)
    private String code;

    @Field(label = "Employees")
    private List<EmpInfo> empIds;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Active")
    private Boolean active;
}
