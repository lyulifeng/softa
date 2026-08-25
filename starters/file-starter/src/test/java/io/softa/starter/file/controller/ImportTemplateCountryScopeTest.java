package io.softa.starter.file.controller;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.Filters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the template listing narrows by when a country is in play.
 *
 * <p>The rule pinned here is the one that would be expensive to get wrong: a template with no country
 * applies to every country, and almost every template is that kind — job grades, cost centres,
 * departments have no country dimension at all. Narrow with a bare equality and the listing comes
 * back empty for every tenant on the release that adds the column, because that is exactly the moment
 * every existing row holds null.
 */
class ImportTemplateCountryScopeTest {

    private final ImportTemplateController controller = new ImportTemplateController();

    private Filters scopeFor(String country) {
        Context context = new Context();
        context.setCompanyCountry(country);
        return ContextHolder.callWith(context, controller::countryScope);
    }

    @Test
    void aTemplateWithNoCountryIsListedAlongsideTheSelectedOne() {
        // Both halves matter. Without the first, a Singapore company loses every country-less
        // template — which is 94% of them. Without the second, it also sees New Zealand's.
        assertThat(scopeFor("SG"))
                .hasToString("[[\"country\",\"IS NOT SET\",null],\"OR\",[\"country\",\"=\",\"SG\"]]");
    }

    @Test
    void noCountrySelectedMeansNoNarrowingAtAll() {
        // Not "narrow to the templates with no country" — before a company is chosen there is nothing
        // to narrow by, and showing every template beats showing none.
        assertThat(scopeFor(null)).isNull();
        assertThat(scopeFor("")).isNull();
    }
}
