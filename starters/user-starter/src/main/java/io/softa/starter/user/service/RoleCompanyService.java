package io.softa.starter.user.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.user.entity.RoleCompany;

/**
 * RoleCompany Model Service Interface.
 *
 * <p>Exists so writes go through {@code AbstractRoleGrantServiceImpl} rather than the generic model
 * CRUD — see the implementation for why that matters.
 */
public interface RoleCompanyService extends EntityService<RoleCompany, Long> {
}
