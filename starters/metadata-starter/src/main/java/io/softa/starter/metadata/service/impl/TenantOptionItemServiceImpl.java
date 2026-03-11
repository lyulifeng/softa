package io.softa.starter.metadata.service.impl;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.metadata.entity.TenantOptionItem;
import io.softa.starter.metadata.service.TenantOptionItemService;
import org.springframework.stereotype.Service;

/**
 * TenantOptionItem Model Service Implementation
 */
@Service
public class TenantOptionItemServiceImpl extends EntityServiceImpl<TenantOptionItem, Long> implements TenantOptionItemService {

}
