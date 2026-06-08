package io.softa.starter.user.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.user.enums.LoginDeviceType;
import io.softa.starter.user.enums.LoginMethod;
import io.softa.starter.user.enums.LoginStatus;

/**
 * UserLoginHistory Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "User Login History",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class UserLoginHistory extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "User ID", required = true)
    private Long userId;

    @Field(label = "Login Method")
    private LoginMethod loginMethod;

    @Field(label = "Login Device Type")
    private LoginDeviceType loginDeviceType;

    @Field(label = "IP Address", length = 64)
    private String ipAddress;

    @Field(label = "User Agent", length = 64)
    private String userAgent;

    @Field(label = "Location", length = 64)
    private String location;

    @Field(label = "Status")
    private LoginStatus status;
}
