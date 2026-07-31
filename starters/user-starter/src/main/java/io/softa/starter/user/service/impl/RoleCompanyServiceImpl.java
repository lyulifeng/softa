package io.softa.starter.user.service.impl;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.softa.starter.user.entity.RoleCompany;
import io.softa.starter.user.service.RoleCompanyService;

/**
 * RoleCompany Model Service Implementation.
 *
 * <p>Extends {@link AbstractRoleGrantServiceImpl} for one reason: every write has to publish a
 * {@code RoleGrantChangedEvent} so the cached {@code PermissionInfo} of every user holding the role is
 * evicted. Without it the grant is read from a snapshot that outlives the change — an administrator
 * adds a company to a role and it does not take effect until the cache expires, with nothing on
 * screen to explain the delay. The three sibling grants ({@code RoleNavigation}, {@code RoleDataScope},
 * {@code RoleSensitiveFieldSet}) all route through this same base for the same reason; going through
 * the generic model CRUD instead would silently skip it.
 */
@Service
public class RoleCompanyServiceImpl extends AbstractRoleGrantServiceImpl<RoleCompany>
        implements RoleCompanyService {

    public RoleCompanyServiceImpl(ApplicationEventPublisher events) {
        super(events);
    }

    @Override
    protected Long roleIdOf(RoleCompany entity) {
        return entity == null ? null : entity.getRoleId();
    }

    @Override
    protected String roleIdField() {
        return "roleId";
    }
}
