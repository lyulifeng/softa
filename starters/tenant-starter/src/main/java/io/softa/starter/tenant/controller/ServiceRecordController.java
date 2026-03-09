package io.softa.starter.tenant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.web.controller.EntityController;
import io.softa.starter.tenant.entity.ServiceRecord;
import io.softa.starter.tenant.service.ServiceRecordService;

/**
 * ServiceRecord Model Controller
 */
@Tag(name = "ServiceRecord")
@RestController
@RequestMapping("/ServiceRecord")
public class ServiceRecordController extends EntityController<ServiceRecordService, ServiceRecord, Long> {

}