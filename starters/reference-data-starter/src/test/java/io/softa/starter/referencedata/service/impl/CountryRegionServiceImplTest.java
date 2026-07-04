package io.softa.starter.referencedata.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.referencedata.entity.CountryRegion;
import io.softa.starter.referencedata.enums.Continent;
import io.softa.starter.referencedata.support.CountryRegionCache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CountryRegionServiceImplTest {

    private CountryRegionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new CountryRegionServiceImpl());
        CountryRegionCache cache = mock(CountryRegionCache.class);
        ReflectionTestUtils.setField(service, "cache", cache);

        // Default cache behavior: pass through to the loader supplier.
        when(cache.getByCode(any(), any())).thenAnswer(inv -> {
            Supplier<CountryRegion> loader = inv.getArgument(1);
            return loader.get();
        });
    }

    @Test
    void findByCodeReturnsEntity() {
        // code-as-id: findByCode resolves via getById (the ISO code IS the PK).
        CountryRegion cn = country("CN", "China", Continent.AS, "CNY", false, true);
        doReturn(Optional.of(cn)).when(service).getById("CN");

        Optional<CountryRegion> result = service.findByCode("CN");

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals("CN", result.get().getId());
        Assertions.assertEquals("CNY", result.get().getCurrencyCode());
        Assertions.assertEquals(Continent.AS, result.get().getContinent());
    }

    @Test
    void findByCodeReturnsEmptyWhenNotFound() {
        doReturn(Optional.empty()).when(service).getById("ZZ");
        Assertions.assertTrue(service.findByCode("ZZ").isEmpty());
    }

    @Test
    void findByContinentReturnsList() {
        List<CountryRegion> asia = List.of(
                country("CN", "China", Continent.AS, "CNY", false, true),
                country("JP", "Japan", Continent.AS, "JPY", false, true),
                country("TW", "Taiwan", Continent.AS, "TWD", false, true));
        doReturn(asia).when(service).searchList(any(FlexQuery.class));

        List<CountryRegion> result = service.findByContinent(Continent.AS);

        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.stream().allMatch(c -> c.getContinent() == Continent.AS));
    }

    @Test
    void findEeaMembersReturnsList() {
        List<CountryRegion> eea = List.of(
                country("DE", "Germany", Continent.EU, "EUR", true, true),
                country("FR", "France", Continent.EU, "EUR", true, true),
                country("IS", "Iceland", Continent.EU, "ISK", true, false),
                country("NO", "Norway", Continent.EU, "NOK", true, false));
        doReturn(eea).when(service).searchList(any(FlexQuery.class));

        List<CountryRegion> result = service.findEeaMembers();

        Assertions.assertEquals(4, result.size());
        Assertions.assertTrue(result.stream().allMatch(CountryRegion::getEea));
    }

    // ---- helpers ----

    private static CountryRegion country(String code, String name, Continent continent,
                                         String currencyCode, boolean eea, boolean hasSubdivisions) {
        CountryRegion c = new CountryRegion();
        c.setId(code);   // code-as-id
        c.setName(name);
        c.setContinent(continent);
        c.setCurrencyCode(currencyCode);
        c.setEea(eea);
        c.setHasSubdivisions(hasSubdivisions);
        return c;
    }
}
