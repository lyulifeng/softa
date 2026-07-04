package io.softa.starter.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import io.softa.starter.ai.config.AiProperties;

/**
 * AI module auto configuration
 */
@ComponentScan
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {
}
