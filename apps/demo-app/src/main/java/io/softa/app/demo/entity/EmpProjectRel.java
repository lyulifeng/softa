package io.softa.app.demo.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * EmpProjectRel Model — join table backing the Employee &lt;-&gt; Project many-to-many relation.
 */
@Data
@Model(label = "Employee Project Relation")
@Index(indexName = "uk_emp_project_rel", fields = {"empId", "projectId"}, unique = true)
@EqualsAndHashCode(callSuper = true)
public class EmpProjectRel extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Employee ID")
    private Long empId;

    @Field(label = "Project ID")
    private Long projectId;

    @Field(label = "Tenant ID")
    private Long tenantId;
}
