package io.softa.starter.referencedata.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.referencedata.entity.LanguageProfile;

/**
 * CRUD service for {@link LanguageProfile}. See entity javadoc for the
 * platform-default + sparse-override tenant scoping pattern.
 */
public interface LanguageProfileService extends EntityService<LanguageProfile, Long> {

}
