package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.Ownership;

/**
 * DesignModelIndex Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Model Index",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        businessKey = {"modelName", "indexName"}
)
public class DesignModelIndex extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio")
    private Long portfolioId;

    @Field(label = "APP ID")
    private Long appId;

    @Field(label = "Model ID", required = true)
    private Long modelId;

    @Field(label = "Model Name", required = true, length = 64)
    private String modelName;

    @Field(label = "Label", required = true, length = 64)
    private String label;

    @Field(label = "Index Name", length = 64)
    private String indexName;

    @Field(label = "Index Fields")
    private List<String> indexFields;

    @Field(label = "Is Unique Index")
    private Boolean uniqueIndex;

    @Field(label = "Ownership")
    private Ownership ownership;

    @Field(label = "Deleted")
    private Boolean deleted;
}
