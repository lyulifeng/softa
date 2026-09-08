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
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        copyable = false,
        // Who logged in from where is per-tenant data, and the audit page is reachable by a tenant
        // admin — unisolated it showed every tenant's login trail, IP and location included (#956).
        //
        // Written by authentication itself, at a point where the tenant IS known (the login has
        // resolved a user), so rows written from here on carry it. Rows written before this change
        // have tenantId NULL and stay invisible to tenants until the release backfill runs; the
        // platform still sees them, since SUPER_ADMIN reads cross-tenant.
        multiTenant = true
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

    @Field
    private LoginMethod loginMethod;

    @Field
    private LoginDeviceType loginDeviceType;

    @Field(label = "IP Address")
    private String ipAddress;

    @Field
    private String userAgent;

    @Field
    private String location;

    @Field
    private LoginStatus status;
}
