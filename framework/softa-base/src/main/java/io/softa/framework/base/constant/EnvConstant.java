package io.softa.framework.base.constant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import io.softa.framework.base.utils.MapUtils;

public interface EnvConstant {
    String USER_ID = "USER_ID";
    String USER_EMP_ID = "USER_EMP_ID";
    String USER_POSITION_ID = "USER_POSITION_ID";
    String USER_DEPT_ID = "USER_DEPT_ID";
    String USER_COMP_ID = "USER_COMP_ID";

    /**
     * The company this request is being made under — {@code Context.companyId}, chosen in the header
     * switcher. NOT the one the caller belongs to: that is {@link #USER_COMP_ID}, which anchors
     * permission rules and must keep meaning "my own company".
     *
     * <p><b>The name and the value differ on purpose.</b> The identifiers here follow
     * {@code Context.companyId} / {@code Context.companyCountry}, so that one vocabulary — company —
     * runs from the header through the context to the placeholder. The <i>values</i> are the stored wire
     * format: they appear inside {@code role_data_scope.data_scopes} rules an administrator wrote, and
     * inside {@code role.dynamic_filter}, so changing a value silently turns a live rule into a literal
     * string comparison that matches nothing (see {@link #ENV_PARAMS}). Renaming those needs a data
     * migration, not an edit here.
     */
    String COMPANY_ID = "SELECTED_COMP_ID";

    /**
     * The country of {@link #COMPANY_ID}, resolved server-side from that company — never taken from the
     * client, which a request naming {@code X-Company-Country} does not change: that header is only read
     * when no company is selected, and this placeholder is null in exactly that case.
     *
     * <p>Strictly the <b>selected</b> company's, which is the one place this vocabulary is narrower than
     * the context field of the same name: it is null when nothing is selected, even though
     * {@code Context.companyCountry} may hold a value in that case — a fallback to the caller's own
     * company for a role that can select none, or a country the request named outright while
     * deliberately sending none. A rule written {@code ["country","=","SELECTED_COMP_COUNTRY"]} then
     * matches nothing rather than quietly matching either, which would widen a scope configured against
     * the header. The second source makes this load-bearing rather than tidy: a client-supplied country
     * has no grant behind it the way a company id does, so a placeholder that followed it would let any
     * holder of such a rule point it at any country. {@code MultiCountryScope} is the one consumer that wants the
     * fallback, and it emits the resolved value instead of this placeholder.
     */
    String COMPANY_COUNTRY = "SELECTED_COMP_COUNTRY";

    String NOW = "NOW";
    String TODAY = "TODAY";
    String YESTERDAY = "YESTERDAY";

    Set<String> TIME_PARAMS = Set.of(NOW, TODAY, YESTERDAY);
    Set<String> EMP_INFO_PARAMS = Set.of(USER_EMP_ID, USER_POSITION_ID, USER_DEPT_ID, USER_COMP_ID);
    /** Placeholders resolved from the selected company; unlike EMP_INFO_PARAMS these need no EmpInfo. */
    Set<String> COMPANY_PARAMS = Set.of(COMPANY_ID, COMPANY_COUNTRY);
    /** Every substitutable placeholder. A placeholder absent here is bound as a literal string, silently. */
    Set<String> ENV_PARAMS = Set.of(USER_ID, USER_EMP_ID, USER_POSITION_ID, USER_DEPT_ID, USER_COMP_ID,
            COMPANY_ID, COMPANY_COUNTRY, NOW, TODAY, YESTERDAY);

    static Map<String, Object> getEnv() {
        return MapUtils.strObj()
                .put(NOW, LocalDateTime.now())
                .put(TODAY, LocalDate.now())
                .put(YESTERDAY, LocalDate.now().minusDays(1))
                .build();
    }

    static LocalDateTime getNow() {
        return LocalDateTime.now();
    }

    static LocalDate getToday() {
        return LocalDate.now();
    }

    static LocalDate getYesterday() {
        return LocalDate.now().minusDays(1);
    }

}
