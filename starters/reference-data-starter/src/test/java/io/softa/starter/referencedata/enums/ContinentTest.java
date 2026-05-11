package io.softa.starter.referencedata.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ContinentTest {

    @Test
    void allSevenContinentsPresent() {
        Assertions.assertEquals(7, Continent.values().length,
                "Continent must enumerate the 7 conventional continents");
    }

    @Test
    void codeLookupResolvesEveryValue() {
        for (Continent c : Continent.values()) {
            Assertions.assertSame(c, Continent.of(c.getCode()),
                    "Continent.of(" + c.getCode() + ") must round-trip to " + c.name());
        }
    }

    @Test
    void ofNullReturnsNull() {
        Assertions.assertNull(Continent.of(null));
    }

    @Test
    void ofUnknownCodeReturnsNull() {
        Assertions.assertNull(Continent.of("ZZ"));
        Assertions.assertNull(Continent.of(""));
    }

    @Test
    void codesAreTwoLetterUpperCase() {
        for (Continent c : Continent.values()) {
            Assertions.assertEquals(2, c.getCode().length(),
                    "Continent code must be 2 letters: " + c.name());
            Assertions.assertEquals(c.getCode().toUpperCase(), c.getCode(),
                    "Continent code must be upper-case: " + c.name());
        }
    }

    @Test
    void namesArePopulated() {
        for (Continent c : Continent.values()) {
            Assertions.assertNotNull(c.getName());
            Assertions.assertFalse(c.getName().isBlank(),
                    "Continent name must not be blank: " + c.name());
        }
    }
}
