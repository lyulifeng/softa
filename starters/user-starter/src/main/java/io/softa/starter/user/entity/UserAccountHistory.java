package io.softa.starter.user.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * UserAccountHistory Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "User Account History",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class UserAccountHistory extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "User ID")
    private Long userId;

    @Field(label = "Password", length = 256)
    private String password;

    @Field(label = "Password Salt", length = 64)
    private String passwordSalt;
}
