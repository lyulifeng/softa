package io.softa.starter.sentry;

import org.springframework.context.annotation.ComponentScan;

/**
 * Sentry module auto configuration. The Sentry SDK itself is auto-configured by
 * sentry-spring-boot-4-starter and stays fully disabled until SENTRY_DSN is set,
 * so depending on this starter is free for environments that do not use Sentry.
 */
@ComponentScan
public class SentryStarterAutoConfiguration {
}
