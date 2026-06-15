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

    @Field(required = true)
    private String modelName;

    @Field(required = true)
    private String label;

    @Field
    private String indexName;

    @Field
    private List<String> indexFields;

    @Field(label = "Is Unique Index")
    private Boolean uniqueIndex;

    @Field
    private Ownership ownership;

    @Field
    private Boolean deleted;
}
