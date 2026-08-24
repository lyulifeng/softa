package io.softa.starter.file.excel.export.support;

import java.lang.reflect.Method;

import io.softa.framework.orm.domain.Filters;
import io.softa.framework.base.enums.Operator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The half of the resolver that decides which set a column belongs to.
 *
 * <p>A relation onto {@code TenantOptionItem} names its option set in the field's own filters, and the
 * dropdown is only correct if that name is read back out. Read the wrong thing and the column offers
 * another set's codes — a list that looks plausible and imports as an unresolvable value.
 *
 * <p>Only the filter-reading is exercised here. What surrounds it — metadata lookups and the option
 * query — needs a live snapshot and a database, so covering it here would mean asserting against
 * mocks of the framework rather than against this class.
 */
class OptionDropdownResolverTest {

    private String optionSetCodeIn(Filters filters) throws Exception {
        Method method = OptionDropdownResolver.class
                .getDeclaredMethod("optionSetCodeIn", Filters.class);
        method.setAccessible(true);
        return (String) method.invoke(new OptionDropdownResolver(), filters);
    }

    @Test
    void readsTheSetOutOfASingleEqualityFilter() throws Exception {
        // The shape every such field declares today: ["optionSetCode", "=", "OrganizationType"].
        assertThat(optionSetCodeIn(Filters.of("optionSetCode", Operator.EQUAL, "OrganizationType")))
                .isEqualTo("OrganizationType");
    }

    @Test
    void findsItAlongsideAnotherCondition() throws Exception {
        // A filter is a tree. Reading a fixed position works until a second condition is added and
        // pushes the one that matters out of place, so the search has to walk it.
        Filters filters = Filters.of("activeFlag", Operator.EQUAL, true)
                .and(Filters.of("optionSetCode", Operator.EQUAL, "ProjectType"));

        assertThat(optionSetCodeIn(filters)).isEqualTo("ProjectType");
    }

    @Test
    void answersNullWhenNoConditionNamesASet() throws Exception {
        // Nothing to narrow by means the list would be every option item the tenant owns, across every
        // set — so the column gets no dropdown rather than a misleading one.
        assertThat(optionSetCodeIn(Filters.of("activeFlag", Operator.EQUAL, true))).isNull();
        assertThat(optionSetCodeIn(null)).isNull();
        assertThat(optionSetCodeIn(new Filters())).isNull();
    }
}
