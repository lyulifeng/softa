package io.softa.starter.user.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.user.enums.OAuthProvider;

/**
 * UserAuthProvider Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "User Auth Provider",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class UserAuthProvider extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "User ID", required = true)
    private Long userId;

    @Field(label = "Provider", required = true)
    private OAuthProvider provider;

    @Field(label = "Provider User ID", required = true, length = 64)
    private String providerUserId;

    @Field(label = "Additional Info", length = 1000)
    private String additionalInfo;
}
