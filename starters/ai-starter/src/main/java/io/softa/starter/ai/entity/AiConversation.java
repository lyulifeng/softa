package io.softa.starter.ai.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * AiConversation Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "AI Conversation",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        copyable = false
)
public class AiConversation extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Conversation Title")
    private String title;

    @Field(label = "Robot ID", fieldType = FieldType.MANY_TO_ONE, relatedModel = AiRobot.class)
    private Long robotId;

    @Field
    private Integer totalTokens;

    @Field(length = 256)
    private String description;

}
