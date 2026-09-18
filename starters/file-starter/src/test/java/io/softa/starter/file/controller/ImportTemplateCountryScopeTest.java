package io.softa.starter.file.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.Filters;

/**
 * What the template listing narrows by: the countries the caller works in.
 *
 * <p>The rule pinned here is the one that would be expensive to get wrong: a template with no country
 * applies to every country, and almost every template is that kind — job grades, cost centres,
 * departments have no country dimension at all. Narrow with a bare membership test and the listing
 * comes back empty for every tenant on the release that adds the column, because that is exactly the
 * moment every existing row holds null.
 */
class ImportTemplateCountryScopeTest {

    private final ImportTemplateController controller = new ImportTemplateController();

    private Filters scopeFor(Set<String> countries, String ownCountry) {
        Context context = new Context();
        context.setGrantedCountries(countries);
        context.setCompanyCountry(ownCountry);
        return ContextHolder.callWith(context, controller::countryScope);
    }

    @Test
    void aTemplateWithNoCountryIsListedAlongsideTheCallersCountries() {
        // Both halves matter. Without the first, an SG + NZ user loses every country-less template —
        // which is 94% of them. Without the second, they also see Malaysia's.
        assertThat(scopeFor(Set.of("SG", "NZ"), null))
                .hasToString("[[\"country\",\"IS NOT SET\",null],\"OR\",[\"country\",\"IN\",[\"NZ\",\"SG\"]]]");
    }

    @Test
    void anEmptySetFallsBackToTheCallersOwnCountry() {
        // A self-service employee whose roles reach no company still imports for their own country.
        assertThat(scopeFor(Set.of(), "SG"))
                .hasToString("[[\"country\",\"IS NOT SET\",null],\"OR\",[\"country\",\"IN\",[\"SG\"]]]");
    }

    @Test
    void nothingKnownMeansNoNarrowingAtAll() {
        // Not "narrow to the templates with no country" — a request that consulted no snapshot has
        // nothing to narrow by, and showing every template beats showing none.
        assertThat(scopeFor(null, null)).isNull();
        assertThat(scopeFor(Set.of(), "")).isNull();
    }
}
