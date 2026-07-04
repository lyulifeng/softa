package io.softa.starter.ai.model;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Supplies the application's {@link ObservationRegistry} to the programmatically-built
 * {@code ChatModel} / {@code ChatClient} instances.
 * <p>
 * Because the AI layer constructs models and clients itself (not via Spring AI
 * auto-configuration), the registry must be passed in explicitly to keep AI calls
 * observable — per the Spring AI docs, {@code ChatClient.create(chatModel)} without it
 * bypasses observability. Falls back to {@link ObservationRegistry#NOOP} when none is
 * configured (e.g. observability disabled).
 */
@Component
@RequiredArgsConstructor
public class AiObservability {

    private final ObjectProvider<ObservationRegistry> observationRegistryProvider;

    public ObservationRegistry registry() {
        return observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
    }
}
