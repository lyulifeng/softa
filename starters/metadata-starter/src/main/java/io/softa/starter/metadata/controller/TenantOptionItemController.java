package io.softa.starter.metadata.controller;

import io.softa.framework.web.controller.EntityController;
import io.softa.starter.metadata.entity.TenantOptionItem;
import io.softa.starter.metadata.service.TenantOptionItemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TenantOptionItem Model Controller
 */
@Tag(name = "TenantOptionItem")
@RestController
@RequestMapping("/TenantOptionItem")
public class TenantOptionItemController extends EntityController<TenantOptionItemService, TenantOptionItem, Long> {

}
