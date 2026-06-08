package io.softa.starter.ai.entity;

import java.io.Serial;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.ai.enums.AiModelProvider;
import io.softa.starter.ai.enums.AiModelType;

/**
 * AiModel Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "AI Model",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        activeControl = true
)
public class AiModel extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Model Name", required = true, length = 64)
    private String name;

    @Field(label = "Model Code", required = true, length = 64)
    private String code;

    @Field(label = "Model Provider")
    private AiModelProvider modelProvider;

    @Field(label = "Model Type")
    private AiModelType modelType;

    @Field(label = "Input Price/1M tokens", length = 32, scale = 8)
    private BigDecimal unitPriceInput;

    @Field(label = "Output price/1M tokens", length = 32, scale = 8)
    private BigDecimal unitPriceOutput;

    @Field(label = "Max Context Tokens")
    private Integer maxTokens;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Active")
    private Boolean active;

    @Field(label = "Deleted")
    private Boolean deleted;
}
