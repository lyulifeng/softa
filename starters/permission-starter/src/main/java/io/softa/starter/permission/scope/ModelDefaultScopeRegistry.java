package io.softa.starter.permission.scope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.softa.starter.permission.entity.ModelDefaultScope;
import io.softa.starter.permission.spi.ScopeType;

/**
 * Which models declare a default row scope, and what it is.
 *
 * <p>One lazily-built map over {@link ModelDefaultScope}. Lazy rather than eager because the rows
 * arrive with the seed, which on a fresh database lands after this bean is wired; and only a
 * non-empty result is cached, so a read taken before the seed is retried rather than pinned to
 * empty — the same reasoning {@code ScopeApplicabilityResolver} uses for its own registry.
 *
 * <p>A row naming no known {@link ScopeType} is dropped with a warning rather than failing the
 * request. The boot validator reports those, which is where a typo should surface; a request is the
 * wrong place to discover one, and treating it as "no declaration" leaves the model on the
 * fail-closed path — the safe reading of a value nobody can make sense of.
 */
@Slf4j
@Component
public class ModelDefaultScopeRegistry {

    private final ModelDefaultScopeReader reader;

    private volatile Map<String, ScopeType> byModel;

    public ModelDefaultScopeRegistry(ModelDefaultScopeReader reader) {
        this.reader = reader;
    }

    /** The scope {@code modelName} falls back to, or {@code null} when it declares none. */
    public ScopeType scopeFor(String modelName) {
        return modelName == null ? null : all().get(modelName);
    }

    /** Every declaration, for the boot report. */
    public Map<String, ScopeType> all() {
        Map<String, ScopeType> cached = byModel;
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> rows = reader.read();
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, ScopeType> loaded = HashMap.newHashMap(rows.size());
        for (Map<String, Object> row : rows) {
            String model = str(row.get("id"));
            ScopeType type = parse(str(row.get("scopeType")), model);
            if (model != null && type != null) {
                loaded.put(model, type);
            }
        }
        Map<String, ScopeType> immutable = Map.copyOf(loaded);
        byModel = immutable;
        return immutable;
    }

    /**
     * Drop the cached map so the next read re-loads it.
     *
     * <p>These rows are edited rarely and deliberately — adding one is a decision about a model's
     * shape, not a runtime knob — so there is no TTL. Whoever changes the table calls this (or
     * restarts) to make the change take effect.
     */
    public void evict() {
        byModel = null;
    }

    private ScopeType parse(String code, String model) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        try {
            return ScopeType.valueOf(code.trim());
        } catch (IllegalArgumentException unknown) {
            log.warn("ModelDefaultScope row for {} names no known ScopeType ('{}'); ignoring it",
                    model, code);
            return null;
        }
    }

    private static String str(Object value) {
        return value == null ? null : StringUtils.trimToNull(value.toString());
    }
}
