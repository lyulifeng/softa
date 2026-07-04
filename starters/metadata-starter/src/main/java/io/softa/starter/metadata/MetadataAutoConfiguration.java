package io.softa.starter.metadata;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import io.softa.starter.metadata.config.MetadataProperties;

/**
 * Metadata management module auto configuration.
 *
 * <p>Wires {@link MetadataProperties} (bound to {@code system.metadata.*}) so
 * the {@code scanner-scope} setting is available to
 * {@code MetadataAnnotationScanner} (reconciles in-scope packages at boot) and
 * {@code MetadataAnnotationChecker} (read-only drift detection when the scope is
 * empty). Both beans are registered unconditionally and gate themselves on the
 * scope.
 */
@ComponentScan
@EnableConfigurationProperties(MetadataProperties.class)
public class MetadataAutoConfiguration {
}
