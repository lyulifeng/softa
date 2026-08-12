package io.softa.starter.message.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorClassifierTest {

    private final ErrorClassifier classifier = new ErrorClassifier();

    @Test
    void serviceLayerMarkerCodes_mapToAuth() {
        assertEquals(ErrorCategory.AUTH, classifier.classify("PROVIDER_NOT_FOUND", null));
        assertEquals(ErrorCategory.AUTH, classifier.classify("PROVIDER_RESOLVE_FAILED", null));
        assertEquals(ErrorCategory.AUTH, classifier.classify("CONFIG_NOT_RESOLVABLE", null));
    }

    @Test
    void configNotResolvable_isNotRetryable() {
        // AUTH → DLQ on first failure; a broken config must not burn the retry budget.
        assertEquals(false, classifier.classify("CONFIG_NOT_RESOLVABLE", null).isRetryable());
    }

    @Test
    void unmatchedCodeAndMessage_fallBackToUnknown() {
        assertEquals(ErrorCategory.UNKNOWN, classifier.classify("X999", "something odd"));
        assertEquals(ErrorCategory.UNKNOWN, classifier.classify(null, null));
    }
}
