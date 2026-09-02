package io.softa.starter.flow.enums;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Error handling strategy for any node.
 * Configurable per-node via {@code NodeErrorConfig.strategy}.
 */
@Getter
@AllArgsConstructor
public enum NodeErrorStrategy {
    /** Propagate the error and fail the flow (default). */
    FAIL("Fail"),
    /** Retry the node up to the configured retry count (immediate, no delay) before failing. */
    RETRY("Retry"),
    ;

    @JsonValue
    private final String type;

    /** Accepted spellings, upper-cased on both sides: the {@code @JsonValue} type and the constant name. */
    private static final Map<String, NodeErrorStrategy> NAMES_MAP = Stream.of(values())
            .collect(Collectors.toMap(NodeErrorStrategy::getType, Function.identity()));

    /**
     * Lenient parser for raw {@code NodeErrorConfig.strategy} values read out of an untyped
     * config map. Not a Jackson hook — typed JSON binding goes through {@link JsonValue}.
     */
    public static NodeErrorStrategy fromValue(String value) {
        if (value == null) {
            return FAIL;
        }
        NodeErrorStrategy strategy = NAMES_MAP.get(value.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported node error strategy: " + value);
        }
        return strategy;
    }
}
