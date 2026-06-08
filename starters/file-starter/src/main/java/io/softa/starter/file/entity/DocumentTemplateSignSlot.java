package io.softa.starter.file.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * DocumentTemplateSignSlot Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Document Template Sign Slot")
public class DocumentTemplateSignSlot extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID", length = 32)
    private String tenantId;

    @Field(label = "Document Template")
    private Long templateId;

    @Field(label = "Slot Name", length = 64)
    private String slotName;

    @Field(label = "Slot Code", length = 64)
    private String slotCode;

    @Field(label = "Sequence")
    private Integer sequence;

    @Field(label = "Placement")
    private JsonNode placement;

    @Field(label = "Description", length = 256)
    private String description;
}
