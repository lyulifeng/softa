package io.softa.starter.studio.meta.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.softa.framework.web.controller.EntityController;
import io.softa.starter.studio.meta.entity.DesignModelOnchange;
import io.softa.starter.studio.meta.service.DesignModelOnchangeService;

/**
 * DesignModelOnchange Model Controller
 */
@Tag(name = "DesignModelOnchange")
@RestController
@RequestMapping("/DesignModelOnchange")
public class DesignModelOnchangeController extends EntityController<DesignModelOnchangeService, DesignModelOnchange, Long> {

}