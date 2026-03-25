package io.softa.starter.studio.meta.service.impl;

import org.springframework.stereotype.Service;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.studio.meta.entity.DesignConfig;
import io.softa.starter.studio.meta.service.DesignConfigService;

/**
 * DesignConfig Model Service Implementation
 */
@Service
public class DesignConfigServiceImpl extends EntityServiceImpl<DesignConfig, Long> implements DesignConfigService {

}