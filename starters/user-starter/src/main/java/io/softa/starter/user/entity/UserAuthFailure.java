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
        label = "User Auth Failure",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class UserAuthFailure extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "User ID")
    private Long userId;

    @Field(label = "Request Params")
    private JsonNode requestParams;

    @Field(label = "Failure Reason", length = 1000)
    private String failureReason;

    @Field(label = "Error Stack", length = 20000)
    private String errorStack;

    @Field(label = "IP Address", length = 64)
    private String ipAddress;

    @Field(label = "User Agent", length = 64)
    private String userAgent;

    @Field(label = "Location", length = 64)
    private String location;
}
