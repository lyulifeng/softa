package io.softa.starter.tenant.service.impl;

import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.PaymentRecord;
import io.softa.starter.tenant.service.PaymentRecordService;

/**
 * PaymentRecord Model Service Implementation
 */
@Service
public class PaymentRecordServiceImpl extends EntityServiceImpl<PaymentRecord, Long> implements PaymentRecordService {

}