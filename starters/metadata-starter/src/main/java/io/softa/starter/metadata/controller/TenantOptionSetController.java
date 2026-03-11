package io.softa.starter.metadata.controller;

import io.softa.framework.web.controller.EntityController;
import io.softa.starter.metadata.entity.TenantOptionSet;
import io.softa.starter.metadata.service.TenantOptionSetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TenantOptionSet Model Controller
 */
@Tag(name = "TenantOptionSet")
@RestController
@RequestMapping("/TenantOptionSet")
public class TenantOptionSetController extends EntityController<TenantOptionSetService, TenantOptionSet, Long> {

}
