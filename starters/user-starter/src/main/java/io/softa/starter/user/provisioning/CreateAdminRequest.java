package io.softa.starter.user.provisioning;

import lombok.Data;

/**
 * Ops request to create a tenant's first admin — an invited {@code UserAccount} in the target tenant
 * granted the {@code TENANT_ADMIN} role. No password: the admin receives an email invitation and
 * sets their own password via the link. Separate from tenant provisioning so an admin can also be
 * (re)created for an existing tenant.
 */
@Data
public class CreateAdminRequest {

    private Long tenantId;
    private String email;
    /** Required — becomes the linked business record's contact phone (e.g. corehr employee profile). */
    private String mobile;
}
