package io.softa.starter.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.annotation.Model;
import io.softa.starter.user.enums.OAuthProvider;
import io.softa.starter.user.service.impl.UserAuthProviderServiceImpl;

/**
 * The auth and security-audit models isolate by tenant, and the one read that cannot is marked.
 *
 * <p>All three carried a {@code tenantId} column while their {@code @Model} said nothing, and that
 * combination is worse than having no column: a model that is not {@code multiTenant} treats
 * {@code tenantId} as readonly, so every insert dropped the value and every read went unfiltered —
 * one tenant admin opening Users &gt; Identity / Security saw every other tenant's auth providers,
 * password rules and login trail (#956).
 *
 * <p>Both halves are pinned here because both fail silently and in opposite directions.
 *
 * <p><b>Losing {@code multiTenant}</b> re-opens the leak with nothing to notice it: the column stays,
 * the pages keep working, the rows are just everyone's again.
 *
 * <p><b>Losing {@code @CrossTenant}</b> breaks social login instead, and not visibly.
 * {@code getUserIdByAuthProvider} is the OAuth callback's "have I seen this account before?" query,
 * asked before anyone is authenticated — no tenant in context. Isolated without the exemption, the
 * added {@code tenant_id = null} predicate matches nothing, every returning social user reads as new,
 * and each login registers another duplicate account. The annotation is load-bearing rather than
 * decorative, and being AOP it is also easy to lose by accident: inline the method into its caller,
 * or call it from within its own class, and the proxy never runs.
 */
class AuthSecurityTenantIsolationTest {

    @Test
    void theAuthAndSecurityModelsIsolateByTenant() {
        List<Class<?>> models = List.of(
                UserAuthProvider.class, UserSecurityPolicy.class, UserLoginHistory.class);

        for (Class<?> model : models) {
            Model annotation = model.getAnnotation(Model.class);
            assertThat(annotation)
                    .as("%s has no @Model at all", model.getSimpleName())
                    .isNotNull();
            assertThat(annotation.multiTenant())
                    .as("%s is reachable by a tenant admin and holds per-tenant rows; without "
                            + "multiTenant its tenantId column is readonly and every read is "
                            + "unfiltered", model.getSimpleName())
                    .isTrue();
        }
    }

    /** The column has to exist for the isolation to have anything to match on. */
    @Test
    void eachOfThemStillDeclaresTheColumnTheIsolationMatchesOn() throws NoSuchFieldException {
        for (Class<?> model : List.of(
                UserAuthProvider.class, UserSecurityPolicy.class, UserLoginHistory.class)) {
            assertThat(model.getDeclaredField("tenantId").getType())
                    .as("%s.tenantId", model.getSimpleName())
                    .isEqualTo(Long.class);
        }
    }

    @Test
    void theOauthLookupStaysCrossTenant() throws NoSuchMethodException {
        Method lookup = UserAuthProviderServiceImpl.class
                .getDeclaredMethod("getUserIdByAuthProvider", OAuthProvider.class, String.class);

        assertThat(lookup.getAnnotation(CrossTenant.class))
                .as("getUserIdByAuthProvider runs before login, so there is no tenant to match on. "
                        + "Without @CrossTenant the isolation added above makes it match nothing, and "
                        + "every returning social user is registered again")
                .isNotNull();
    }
}
