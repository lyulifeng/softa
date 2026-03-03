package io.softa.starter.metadata.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.web.controller.EntityController;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.service.SysModelService;

/**
 * SysModel Model Controller
 */
@Tag(name = "SysModel")
@RestController
@RequestMapping("/SysModel")
public class SysModelController extends EntityController<SysModelService, SysModel, Long> {

}