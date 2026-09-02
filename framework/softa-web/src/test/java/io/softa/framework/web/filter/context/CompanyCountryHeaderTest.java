package io.softa.framework.web.filter.context;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.context.Context;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The country a company-less request may name for itself.
 *
 * <p>A screen that must reach across the caller's companies sends no {@code X-Company-Id}, which
 * switches the company narrowing off — but that absence says nothing about which country it wants,
 * and it cannot: a client that never sends the header and a screen that drops it deliberately look
 * identical here. {@code X-Company-Country} is the request saying the second half out loud.
 *
 * <p>This header is caller-supplied and, unlike the company id, has no grant standing behind it —
 * nothing bounds a country the way {@code appendCompanyGrant} bounds a company. What makes accepting
 * it safe is that it stays out of authorization: {@code SELECTED_COMP_COUNTRY} resolves to null
 * without a selected company, so a CUSTOM scope rule written against the header keeps matching what
 * it matched when it was written. That property is asserted in {@code FilterUnitParserEnvTest},
 * against the parser itself — restating it here would only re-assert this class's own precondition.
 */
class CompanyCountryHeaderTest {

    private final ContextBuilder contextBuilder = new ContextBuilder();

    private static MockHttpServletRequest requestWith(String country) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (country != null) {
            request.addHeader(BaseConstant.COMPANY_COUNTRY_HEADER, country);
        }
        return request;
    }

    @Test
    void aCountryNamedWithoutACompanyIsTaken() {
        Context context = new Context();

        contextBuilder.setCompanyCountryFromRequest(requestWith("SG"), context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
        assertThat(context.getCompanyId()).isNull();
    }

    @Test
    void aSelectedCompanyWinsOverTheCountryHeader() {
        // The country is resolved from the selected company server-side. Honouring both would let a
        // request assert a Singapore company and a Malaysian country, and everything downstream would
        // believe it — two sources of truth for one fact, disagreeing silently.
        Context context = new Context();
        context.setCompanyId(8712L);

        contextBuilder.setCompanyCountryFromRequest(requestWith("MY"), context);

        assertThat(context.getCompanyCountry())
                .as("the enricher resolves it from the company; the header must not pre-empt that")
                .isNull();
    }

    @Test
    void aBlankHeaderLeavesTheFallbackItsTurn() {
        // Blank is not "no country" — it is a client sending an empty value, and treating it as an
        // answer would suppress CompanyCountryEnricher's fallback and leave a self-service employee
        // looking at every country's value domains.
        Context context = new Context();

        contextBuilder.setCompanyCountryFromRequest(requestWith("   "), context);

        assertThat(context.getCompanyCountry()).isNull();
    }

    // The fourth property — that this header never reaches SELECTED_COMP_COUNTRY — is pinned where
    // the guard lives, in FilterUnitParserEnvTest, driving the parser rather than restating the
    // condition here.
}
