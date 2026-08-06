package io.softa.framework.orm.jdbc.database.parser;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.constant.EnvConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.EmpInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a stored scope rule's env placeholder resolves to. These are the values a tenant's
 * {@code role_data_scope} rows were written against, so a change here silently changes which rows an
 * existing role can read — the reason each case is pinned rather than covered by one happy path.
 *
 * <p>The case that motivated the class is {@code SELECTED_COMP_COUNTRY} under the no-company fallback:
 * {@code Context.companyCountry} may be populated with nothing selected (a role granted no company
 * falls back to the one it belongs to, so that per-country value domains still narrow), and this
 * placeholder must <b>not</b> follow it there. A rule written
 * {@code ["country","=","SELECTED_COMP_COUNTRY"]} matches nothing when nothing is selected; letting it
 * pick up the fallback would widen a data scope that was configured against the header, without
 * anything changing in the configuration.
 */
class FilterUnitParserEnvTest {

    private static Object resolve(Context context, String placeholder) {
        return ContextHolder.callWith(context, () -> FilterUnitParser.convertEnvParameter(placeholder));
    }

    // ---- the selected company --------------------------------------------

    @Test
    void resolvesTheSelectedCompanyAndItsCountry() {
        Context context = new Context();
        context.setCompanyId(8712L);
        context.setCompanyCountry("SG");

        assertThat(resolve(context, EnvConstant.COMPANY_ID)).isEqualTo(8712L);
        assertThat(resolve(context, EnvConstant.COMPANY_COUNTRY)).isEqualTo("SG");
    }

    @Test
    void resolvesToNullWhenNothingIsSelected() {
        Context context = new Context();

        assertThat(resolve(context, EnvConstant.COMPANY_ID)).isNull();
        assertThat(resolve(context, EnvConstant.COMPANY_COUNTRY)).isNull();
    }

    @Test
    void doesNotFollowTheCountryFallbackWhenNothingIsSelected() {
        // The state the enricher leaves behind for a role that can select no company: a country with
        // no selection. MultiCountryScope wants it — value domains are data correctness, and showing
        // someone another country's pass types is wrong regardless of what they may read — but a scope
        // rule naming this placeholder was configured against the header, and must keep matching what
        // it matched before the fallback existed.
        Context context = new Context();
        context.setCompanyCountry("SG");

        assertThat(resolve(context, EnvConstant.COMPANY_COUNTRY)).isNull();
    }

    // ---- the caller's own company ----------------------------------------

    @Test
    void theAffiliationIsItsOwnPlaceholderAndIsUnaffected() {
        // USER_COMP_ID keeps meaning "the company I belong to" whether or not one is selected. It is
        // the placeholder a rule should name when it wants the affiliation — the existence of the
        // fallback must not turn SELECTED_COMP_COUNTRY into a second way of asking for it.
        Context context = new Context();
        EmpInfo empInfo = new EmpInfo();
        empInfo.setCompanyId(4242L);
        context.setEmpInfo(empInfo);

        assertThat(resolve(context, EnvConstant.USER_COMP_ID)).isEqualTo(4242L);

        context.setCompanyId(8712L);
        assertThat(resolve(context, EnvConstant.USER_COMP_ID)).isEqualTo(4242L);
    }
}
