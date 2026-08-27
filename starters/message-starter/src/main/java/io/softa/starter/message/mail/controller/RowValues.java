package io.softa.starter.message.mail.controller;

import java.util.Map;

/**
 * Tiny readers for the raw write payloads handled by the shadowed write
 * endpoints. JSON values arrive as Boolean / Number / String depending on
 * the client, so reads are type-tolerant.
 */
final class RowValues {

    private RowValues() {
    }

    /** Whether the payload carries {@code field} with a truthy value. */
    static boolean isTrue(Map<String, Object> row, String field) {
        Object value = row == null ? null : row.get(field);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /** The payload's {@code id}, or null when absent. */
    static Long id(Map<String, Object> row) {
        Object value = row == null ? null : row.get("id");
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
