package io.softa.starter.metadata.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.softa.framework.web.controller.EntityController;
import io.softa.starter.metadata.entity.UserDefaultView;
import io.softa.starter.metadata.service.UserDefaultViewService;

/**
 * UserDefaultView Model Controller
 */
@Tag(name = "UserDefaultView")
@RestController
@RequestMapping("/UserDefaultView")
public class UserDefaultViewController extends EntityController<UserDefaultViewService, UserDefaultView, Long> {

}