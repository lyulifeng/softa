package io.softa.starter.user.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.user.enums.AccountStatus;

/**
 * UserAccount Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "User Account",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        searchName = {"nickname", "username"}
)
public class UserAccount extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "Nickname", length = 64)
    private String nickname;

    @Field(label = "Username", length = 64)
    private String username;

    @Field(label = "Password", length = 256)
    private String password;

    @Field(label = "Password Salt", length = 64)
    private String passwordSalt;

    @Field(label = "Email", length = 64)
    private String email;

    @Field(label = "Mobile", length = 64)
    private String mobile;

    @Field(label = "Activation Time")
    private LocalDateTime activationTime;

    @Field(label = "Policy ID")
    private Long policyId;

    @Field(label = "Locked")
    private Boolean locked;

    @Field(label = "Status")
    private AccountStatus status;
}
