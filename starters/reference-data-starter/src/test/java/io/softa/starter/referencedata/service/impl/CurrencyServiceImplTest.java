package io.softa.starter.referencedata.service.impl;

import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.starter.referencedata.entity.Currency;
import io.softa.starter.referencedata.support.CurrencyCache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CurrencyServiceImplTest {

    private CurrencyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new CurrencyServiceImpl());
        CurrencyCache cache = mock(CurrencyCache.class);
        ReflectionTestUtils.setField(service, "cache", cache);

        when(cache.getByCode(any(), any())).thenAnswer(inv -> {
            Supplier<Currency> loader = inv.getArgument(1);
            return loader.get();
        });
    }

    @Test
    void findByCodeReturnsUsd() {
        // code-as-id: findByCode resolves via getById (the ISO code IS the PK).
        Currency usd = currency("USD", "840", "US Dollar", "$", 2);
        doReturn(Optional.of(usd)).when(service).getById("USD");

        Optional<Currency> result = service.findByCode("USD");

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals("USD", result.get().getId());
        Assertions.assertEquals(2, result.get().getDecimalPlaces());
    }

    @Test
    void findByCodeReturnsJpyWithZeroDecimals() {
        Currency jpy = currency("JPY", "392", "Japanese Yen", "¥", 0);
        doReturn(Optional.of(jpy)).when(service).getById("JPY");

        Optional<Currency> result = service.findByCode("JPY");

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(0, result.get().getDecimalPlaces(),
                "JPY must have 0 fraction digits per ISO 4217");
    }

    @Test
    void findByCodeReturnsBhdWithThreeDecimals() {
        Currency bhd = currency("BHD", "048", "Bahraini Dinar", "BD", 3);
        doReturn(Optional.of(bhd)).when(service).getById("BHD");

        Optional<Currency> result = service.findByCode("BHD");

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(3, result.get().getDecimalPlaces(),
                "BHD must have 3 fraction digits per ISO 4217");
    }

    @Test
    void findByCodeReturnsEmptyWhenNotFound() {
        doReturn(Optional.empty()).when(service).getById("XYZ");
        Assertions.assertTrue(service.findByCode("XYZ").isEmpty());
    }

    // ---- helpers ----

    private static Currency currency(String code, String numericCode, String name,
                                     String symbol, int decimalPlaces) {
        Currency c = new Currency();
        c.setId(code);   // code-as-id
        c.setNumericCode(numericCode);
        c.setName(name);
        c.setSymbol(symbol);
        c.setDecimalPlaces(decimalPlaces);
        return c;
    }
}
