package io.softa.starter.tenant.service.impl;

import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.ServiceOrder;
import io.softa.starter.tenant.service.ServiceOrderService;

/**
 * ServiceOrder Model Service Implementation
 */
@Service
public class ServiceOrderServiceImpl extends EntityServiceImpl<ServiceOrder, Long> implements ServiceOrderService {

}