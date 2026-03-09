package io.softa.starter.tenant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.web.controller.EntityController;
import io.softa.starter.tenant.entity.ServiceOrder;
import io.softa.starter.tenant.service.ServiceOrderService;

/**
 * ServiceOrder Model Controller
 */
@Tag(name = "ServiceOrder")
@RestController
@RequestMapping("/ServiceOrder")
public class ServiceOrderController extends EntityController<ServiceOrderService, ServiceOrder, Long> {

}