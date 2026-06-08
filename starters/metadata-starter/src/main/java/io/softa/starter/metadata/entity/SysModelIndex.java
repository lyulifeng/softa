package io.softa.starter.metadata.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.Ownership;

/**
 * SysModelIndex — metadata catalog row describing a model's database index.
 *
 * <p>Self-described via {@code @Model} + per-field {@code @Field} so the
 * scanner manages its schema like any other SYSTEM_MODEL.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "System Model Index",
        businessKey = {"modelName", "indexName"},
        description = "Metadata catalog of indexes"
)
public class SysModelIndex extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Label")
    private String label;

    @Field(label = "Model Name", required = true)
    private String modelName;

    @Field(label = "Model ID")
    private Long modelId;

    @Field(label = "Index Name", required = true)
    private String indexName;

    @Field(label = "Index Fields", required = true)
    private List<String> indexFields;

    @Field(label = "Is Unique Index")
    private Boolean uniqueIndex;

    @Field(label = "Ownership")
    private Ownership ownership;
}
