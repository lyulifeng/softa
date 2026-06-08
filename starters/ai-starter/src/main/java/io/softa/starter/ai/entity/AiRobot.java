package io.softa.starter.ai.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.ai.enums.AiModelProvider;

/**
 * AiRobot Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "AI Robot",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        activeControl = true,
        description = "Robots that pre-define system prompts and parameter configurations."
)
public class AiRobot extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Robot Name", required = true, length = 64)
    private String name;

    @Field(label = "Robot Code", length = 64)
    private String code;

    @Field(label = "AI Model ID", fieldType = FieldType.MANY_TO_ONE, relatedModel = AiModel.class, required = true)
    private Long aiModelId;

    @Field(label = "AI Model Code", length = 64)
    private String aiModel;

    @Field(label = "AI Provider")
    private AiModelProvider aiProvider;

    @Field(label = "System Prompt", length = 20000)
    private String systemPrompt;

    @Field(label = "Model Max Context Tokens")
    private Integer modelMaxTokens;

    @Field(label = "Input Tokens Limit")
    private Integer inputTokensLimit;

    @Field(label = "Output Tokens Limit")
    private Integer outputTokensLimit;

    @Field(label = "Temperature")
    private Double temperature;

    @Field(label = "Enable Stream Output")
    private Boolean stream;

    @Field(label = "Presence Penalty")
    private Double presencePenalty;

    @Field(label = "Frequency Penalty")
    private Double frequencyPenalty;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Active")
    private Boolean active;

    @Field(label = "Deleted")
    private Boolean deleted;
}
