package io.softa.starter.user.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * UserAuthFailure Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        copyable = false
)
public class UserAuthFailure extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "User ID")
    private Long userId;

    @Field
    private JsonNode requestParams;

    @Field(length = 1000)
    private String failureReason;

    @Field(length = 20000)
    private String errorStack;

    @Field(label = "IP Address")
    private String ipAddress;

    @Field
    private String userAgent;

    @Field
    private String location;
}
