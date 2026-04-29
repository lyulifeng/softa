package io.softa.starter.metadata.sequence.enums;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * How often the sequence counter resets back to {@code start_value}.
 * Each cadence corresponds to a {@code current_key} format used both as the
 * reset boundary detector ({@code last_reset_key} comparison) and as the
 * source for date tokens ({@code yyyy} / {@code MM} / {@code dd}) at render
 * time.
 *
 * <ul>
 *   <li>{@link #NONE}    — never reset; current_key = "" (empty string, NOT null)</li>
 *   <li>{@link #YEARLY}  — reset on year boundary; current_key = "yyyy"</li>
 *   <li>{@link #MONTHLY} — reset on month boundary; current_key = "yyyy-MM"</li>
 *   <li>{@link #DAILY}   — reset on day boundary; current_key = "yyyy-MM-dd"</li>
 * </ul>
 */
public enum ResetCadence {

    NONE("") {
        @Override
        public String computeKey(LocalDateTime time) {
            return "";
        }
    },

    YEARLY("yyyy") {
        @Override
        public String computeKey(LocalDateTime time) {
            return time.format(DateTimeFormatter.ofPattern(pattern));
        }
    },

    MONTHLY("yyyy-MM") {
        @Override
        public String computeKey(LocalDateTime time) {
            return time.format(DateTimeFormatter.ofPattern(pattern));
        }
    },

    DAILY("yyyy-MM-dd") {
        @Override
        public String computeKey(LocalDateTime time) {
            return time.format(DateTimeFormatter.ofPattern(pattern));
        }
    };

    protected final String pattern;

    ResetCadence(String pattern) {
        this.pattern = pattern;
    }

    /**
     * Compute the reset key for the given moment.
     * Used by sequence allocation to compare against {@code last_reset_key}
     * and by template rendering to source date tokens.
     */
    public abstract String computeKey(LocalDateTime time);
}
