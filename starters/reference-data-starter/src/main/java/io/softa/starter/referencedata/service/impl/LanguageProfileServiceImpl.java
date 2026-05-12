package io.softa.starter.referencedata.service.impl;

import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.referencedata.entity.LanguageProfile;
import io.softa.starter.referencedata.service.LanguageProfileService;

@Service
public class LanguageProfileServiceImpl extends EntityServiceImpl<LanguageProfile, Long>
        implements LanguageProfileService {

}
