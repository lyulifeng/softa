package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * DesignOptionSetTrans Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Option Set Translation"
)
public class DesignOptionSetTrans extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Language Code", required = true, length = 64)
    private String languageCode;

    @Field(label = "Row ID")
    private Long rowId;

    @Field(label = "Label", length = 64)
    private String label;

    @Field(label = "Description", length = 256)
    private String description;
}
