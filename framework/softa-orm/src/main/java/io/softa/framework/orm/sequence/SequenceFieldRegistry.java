package io.softa.framework.orm.sequence;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Holds the sequence service binding for the sequence-aware FieldProcessor.
 * <p>
 * The registry lifecycle is intentionally minimal under v1:
 * <ul>
 *   <li>{@link SequenceService} is provided as {@code Optional<>} so the
 *       framework keeps working even if no sequence implementation starter
 *       is on the classpath.</li>
 *   <li>The registry installs itself into {@link SequenceFieldRegistryHolder}
 *       at {@code @PostConstruct} time, giving non-DI components like
 *       {@code DataCreatePipeline} (which is {@code new}'d per call) a static
 *       access point.</li>
 * </ul>
 * The actual (model, field) → autoSequence mapping lives on
 * {@code MetaField.autoSequence}; an external initializer (in the sequence
 * starter) scans {@code sys_sequence} once at {@code ApplicationReadyEvent}
 * and writes the boolean back via {@code MetaFieldInternalAccess}.
 * After init the registry is effectively read-only for the v1 path.
 */
@Component
public class SequenceFieldRegistry {

    private final Optional<SequenceService> sequenceService;

    public SequenceFieldRegistry(Optional<SequenceService> sequenceService) {
        this.sequenceService = sequenceService;
    }

    @PostConstruct
    void registerHolder() {
        SequenceFieldRegistryHolder.set(this);
    }

    public boolean isEnabled() {
        return sequenceService.isPresent();
    }

    public SequenceService getService() {
        return sequenceService.orElseThrow(() ->
                new IllegalStateException("SequenceService is not available; no sequence starter on classpath"));
    }
}
