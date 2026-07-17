package io.softa.starter.user.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

import io.softa.framework.base.utils.JsonUtils;

/**
 * Lenient JSON-array → Java collection converters for {@code JsonNode}
 * columns ({@code permission.endpoints} / {@code role_navigation.dataScopes}
 * / {@code sensitive_field_set.fieldCodes} / {@code role_navigation.permissionIds}
 * etc.). Centralised here so consumers don't drift in subtle ways — e.g.
 * one consumer accepting numeric elements as stringified ids while another
 * silently dropping them.
 *
 * <p>Conventions:
 * <ul>
 *   <li>{@code null} / non-array nodes → empty result, never throw.</li>
 *   <li>Non-string elements → see {@link #toStringList(JsonNode, boolean)}'s
 *       {@code coerceNumeric} flag. Default ({@link #toStringList(JsonNode)})
 *       is strict (string-only) to match the original SensitiveFieldSetCache
 *       behaviour, which is the safest default for new code.</li>
 * </ul>
 *
 * <h3>Relationship to {@code JsonUtils}</h3>
 * The strict-string list conversion now lives in framework
 * {@code JsonUtils.toStringList(Object, boolean)} (2026-07-17) and these
 * {@code toStringList} methods <b>delegate</b> to it — the only difference is the
 * empty contract: framework returns {@code null} (so callers can tell absent from
 * empty), whereas this util coalesces to {@code List.of()} to match the
 * SFS-cache / FieldFilter callers that never null-check. The {@code Set} and
 * {@code Long} variants below have no framework equivalent and stay here.
 * (Do NOT confuse with the older {@code JsonUtils.jsonNodeToStringList}, which uses
 * {@code convertValue} = stringify-everything, not strict-string.)
 */
public final class JsonArrayUtils {

    private JsonArrayUtils() {}

    /**
     * Strict version: only {@link JsonNode#isString()} elements are kept;
     * numbers / booleans / nested objects are silently dropped. Suitable
     * for field-name / set-id columns where numeric elements would be a
     * data error, not a legitimate value.
     */
    public static List<String> toStringList(JsonNode node) {
        return toStringList(node, false);
    }

    /**
     * @param coerceNumeric when true, JsonNode numeric leaves are converted
     *                       to their string form. Use for id columns
     *                       (permission ids, role_navigation.permissionIds)
     *                       where ids may be persisted as numbers in some
     *                       JSON dialects.
     */
    public static List<String> toStringList(JsonNode node, boolean coerceNumeric) {
        List<String> out = JsonUtils.toStringList(node, coerceNumeric);
        return out == null ? List.of() : out;
    }

    /** Strict-string variant returning a Set (dedup applied). */
    public static Set<String> toStringSet(JsonNode node) {
        return new HashSet<>(toStringList(node));
    }

    /** Numeric-coercing variant returning a Set. */
    public static Set<String> toStringSet(JsonNode node, boolean coerceNumeric) {
        return new HashSet<>(toStringList(node, coerceNumeric));
    }

    /**
     * Lenient single-node → Long. Accepts numeric leaves (returns
     * {@code node.asLong()}), string leaves (parses as decimal), null /
     * non-leaf nodes (returns {@code null}). Use for id columns where the
     * upstream JSON dialect mixes numbers and stringified numbers
     * (frontend convention serializes ids as strings; tests / older seeds
     * may persist them as numbers).
     */
    public static Long toLong(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asLong();
        if (node.isString()) {
            String s = node.asString();
            if (s == null || s.isEmpty()) return null;
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Array of mixed-form id elements → deduped Long list (preserves
     * insertion order via {@link java.util.LinkedHashSet} discipline at
     * the caller — this method itself returns a plain list with no
     * dedup). Non-coercible elements ({@code null}, booleans, objects)
     * are silently dropped, matching the {@link #toStringList} contract.
     */
    public static List<Long> toLongList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<Long> out = new ArrayList<>(node.size());
        for (JsonNode el : node) {
            Long v = toLong(el);
            if (v != null) out.add(v);
        }
        return out;
    }
}
