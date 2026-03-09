package io.softa.starter.tenant.service.impl;

import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.ServiceProduct;
import io.softa.starter.tenant.service.ServiceProductService;

/**
 * ServiceProduct Model Service Implementation
 */
@Service
public class ServiceProductServiceImpl extends EntityServiceImpl<ServiceProduct, Long> implements ServiceProductService {

}