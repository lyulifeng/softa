package io.softa.starter.user.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.user.enums.UserRoleSource;

/**
 * UserRoleRel — user-to-role assignment join row. The same (user, role)
 * pair MAY have BOTH a MANUAL row (admin-added) and a DYNAMIC row
 * (auto-synced by DynamicRoleSyncJob) at the schema level — the UNIQUE
 * constraint is (tenant_id, user_id, role_id, source). The wizard's
 * convention enforces a single row per pair (Manual takes precedence),
 * but the schema allows both for compatibility / future audit needs.
 *
 * Deleting a MANUAL row leaves the DYNAMIC row intact (prevents accidental
 * de-authorization). Dynamic rows only refresh on cron sync, not on direct
 * delete.
 *
 * <p><b>Metadata note:</b> {@code io.softa.starter.user.entity} is NOT in scanner-scope; annotations
 * mirror the studio-managed live {@code sys_field} (not reconciled at runtime). Live is {@code multiTenant}
 * and also carries a {@code deleted} column (soft-delete flag) which is not declared as a Java field here.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG, multiTenant = true)
@Index(indexName = "uk_user_role_rel_tenant_user_role_source",
        fields = {"tenantId", "userId", "roleId", "source"}, unique = true,
        message = "This role is already assigned to the user with this source.")
public class UserRoleRel extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "User", fieldType = FieldType.MANY_TO_ONE, relatedModel = UserAccount.class,
            description = "User ID (FK user_account.id)")
    private Long userId;

    @Field(label = "Role", fieldType = FieldType.MANY_TO_ONE, relatedModel = Role.class,
            description = "Role ID (FK role.id)")
    private Long roleId;

    @Field(description = "Source: MANUAL (admin grant) or DYNAMIC (DynamicRoleSyncJob auto-assign)")
    private UserRoleSource source;
}
