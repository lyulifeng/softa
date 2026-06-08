package io.softa.starter.ai.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.ai.enums.AiMessageRole;
import io.softa.starter.ai.enums.AiMessageStatus;

/**
 * AiMessage Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "AI Message",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        defaultOrder = {"createdTime"}
)
public class AiMessage extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Robot ID")
    private Long robotId;

    @Field(label = "Conversation ID", required = true)
    private Long conversationId;

    @Field(label = "Role", required = true)
    private AiMessageRole role;

    @Field(label = "Content", length = 20000)
    private String content;

    @Field(label = "Tokens")
    private Integer tokens;

    @Field(label = "Stream Output")
    private Boolean stream;

    @Field(label = "Parent Message ID")
    private Long parentId;

    @Field(label = "Status")
    private AiMessageStatus status;
}
