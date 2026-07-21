package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * SysPreData Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "System Predefined Data")
public class SysPreData extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field
    private String model;

    @Field(label = "Pre ID", required = true, length = 128)
    private String preId;

    @Field(label = "Row ID", length = 128)
    private String rowId;

    @Field
    private Boolean frozen;

    @Field(label = "Tenant ID")
    private Long tenantId;
}
