package io.softa.starter.referencedata.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Continent enumeration — 7-continent model (the most widely-used scheme
 * across education, UN regional groupings, and IAB taxonomies).
 *
 * <p>Codes are 2-letter to keep persistence compact and unambiguous (the
 * 7-continent set has no ISO standard so the codes are project conventions
 * rather than ISO 3166 / UN M49 codes). Use these as enum constants in code
 * and persist them as the {@link #code} string.
 *
 * <p>Lives in {@code reference-data-starter} alongside {@code CountryRegion}
 * and {@code Currency} because the only consumer of {@code Continent} is
 * {@code CountryRegion.continent}; there is no realistic "I need continents
 * but not countries" scenario. 7 stable values, no rich data — enum is the
 * right shape (in contrast to country/currency which warrant entities).
 */
@Getter
@AllArgsConstructor
public enum Continent {
    AS("AS", "Asia"),
    EU("EU", "Europe"),
    AF("AF", "Africa"),
    NA("NA", "North America"),
    SA("SA", "South America"),
    OC("OC", "Oceania"),
    AN("AN", "Antarctica"),
    ;

    @JsonValue
    private final String code;

    private final String name;

    private static final Map<String, Continent> CODE_MAP = Stream.of(values())
            .collect(Collectors.toMap(Continent::getCode, Function.identity()));

    /**
     * Resolve a {@link Continent} from its code. Returns {@code null} when
     * the input is null or doesn't match any known continent.
     */
    public static Continent of(String code) {
        if (code == null) return null;
        return CODE_MAP.get(code);
    }
}
