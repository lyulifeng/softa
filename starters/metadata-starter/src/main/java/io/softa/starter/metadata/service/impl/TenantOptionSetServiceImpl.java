package io.softa.starter.metadata.service.impl;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.metadata.entity.TenantOptionSet;
import io.softa.starter.metadata.service.TenantOptionSetService;
import org.springframework.stereotype.Service;

/**
 * TenantOptionSet Model Service Implementation
 */
@Service
public class TenantOptionSetServiceImpl extends EntityServiceImpl<TenantOptionSet, Long> implements TenantOptionSetService {

}
