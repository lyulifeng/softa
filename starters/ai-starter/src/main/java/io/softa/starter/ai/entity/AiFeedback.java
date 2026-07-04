package io.softa.starter.ai.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * AiFeedback Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "AI Response Feedback",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        copyable = false
)
public class AiFeedback extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Conversation ID", required = true)
    private Long conversationId;

    @Field(label = "Message ID", required = true)
    private Long messageId;

    @Field(label = "Feedback Content", length = 256)
    private String feedback;

}
