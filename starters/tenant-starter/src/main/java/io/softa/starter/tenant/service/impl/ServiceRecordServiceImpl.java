package io.softa.starter.tenant.service.impl;

import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.ServiceRecord;
import io.softa.starter.tenant.service.ServiceRecordService;

/**
 * ServiceRecord Model Service Implementation
 */
@Service
public class ServiceRecordServiceImpl extends EntityServiceImpl<ServiceRecord, Long> implements ServiceRecordService {

}