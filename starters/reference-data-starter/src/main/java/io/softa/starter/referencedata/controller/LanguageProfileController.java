package io.softa.starter.referencedata.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.web.controller.EntityController;
import io.softa.starter.referencedata.entity.LanguageProfile;
import io.softa.starter.referencedata.service.LanguageProfileService;

/**
 * LanguageProfile Controller. CRUD via the metadata-driven generic endpoints.
 */
@Tag(name = "LanguageProfile")
@RestController
@RequestMapping("/LanguageProfile")
public class LanguageProfileController
        extends EntityController<LanguageProfileService, LanguageProfile, Long> {

}
