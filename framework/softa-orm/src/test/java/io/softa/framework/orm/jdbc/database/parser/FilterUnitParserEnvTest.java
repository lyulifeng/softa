package io.softa.framework.orm.jdbc.database.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.constant.EnvConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.EmpInfo;
import io.softa.framework.base.exception.IllegalArgumentException;

/**
 * What a stored scope rule's env placeholder resolves to. These are the values a tenant's
 * {@code role_data_scope} rows were written against, so a change here silently changes which rows an
 * existing role can read — the reason each case is pinned rather than covered by one happy path.
 *
 * <p>The selected-company placeholders ({@code SELECTED_COMP_ID} / {@code SELECTED_COMP_COUNTRY}) are
 * gone with the header switcher that fed them. A rule still naming one must fail loudly at SQL-build
 * time rather than bind the literal string and match nothing: the second looks like an empty result,
 * the first names the rule to fix. No stored rule used them at the time of removal.
 */
class FilterUnitParserEnvTest {

    private static Object resolve(Context context, String placeholder) {
        return ContextHolder.callWith(context, () -> FilterUnitParser.convertEnvParameter(placeholder));
    }

    @Test
    void theAffiliationIsItsOwnPlaceholder() {
        // USER_COMP_ID means "the company I belong to", and is the placeholder a rule names when it
        // wants the affiliation.
        Context context = new Context();
        EmpInfo empInfo = new EmpInfo();
        empInfo.setCompanyId(4242L);
        context.setEmpInfo(empInfo);

        assertThat(resolve(context, EnvConstant.USER_COMP_ID)).isEqualTo(4242L);
    }

    @Test
    void theRetiredSelectionPlaceholdersAreRejectedNotBoundAsLiterals() {
        Context context = new Context();
        context.setCompanyCountry("SG");

        assertThatThrownBy(() -> resolve(context, "SELECTED_COMP_ID")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolve(context, "SELECTED_COMP_COUNTRY")).isInstanceOf(IllegalArgumentException.class);
    }
}
