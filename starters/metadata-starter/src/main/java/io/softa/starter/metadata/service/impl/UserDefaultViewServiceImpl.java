package io.softa.starter.metadata.service.impl;

import org.springframework.stereotype.Service;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.metadata.entity.UserDefaultView;
import io.softa.starter.metadata.service.UserDefaultViewService;

/**
 * UserDefaultView Model Service Implementation
 */
@Service
public class UserDefaultViewServiceImpl extends EntityServiceImpl<UserDefaultView, Long> implements UserDefaultViewService {

}