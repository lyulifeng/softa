package io.softa.starter.user;

import org.springframework.context.annotation.ComponentScan;

/**
 * User module auto configuration.
 *
 * <p>user-starter no longer registers any permission-starter SPI bean — the
 * permission engine builds the snapshot itself (约定读 the RBAC config models) and
 * ships its own {@code @ConditionalOnMissingBean} defaults for the endpoint /
 * sensitive-field-set sources. So there is nothing to order before
 * permission-starter, and this module carries no compile dependency on it.
 */
@ComponentScan
public class UserAutoConfiguration {
}
