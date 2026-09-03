package io.softa.starter.user.entity;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.annotation.Index;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The uniqueness contract behind identifier-based login (A3), asserted on the models themselves.
 *
 * <p>These three indexes are one decision, and reading any of them alone gets it wrong:
 *
 * <ul>
 *   <li>a LOGIN identifier must be unique — two people holding one value cannot be told apart, and
 *       a code sent there says nothing about which of them is signing in;</li>
 *   <li>a WORK contact need not be — a shop's phone or a shared floor handset on many accounts is
 *       ordinary, and forbidding it would stop HR from creating those employees at all;</li>
 *   <li>so the work email is unique WITHIN a tenant only. Globally unique made a second customer's
 *       hire fail on a first customer's data, and said an account exists elsewhere while doing it.</li>
 * </ul>
 *
 * <p>Asserted here because the annotations are the only place the rule exists: dropping one
 * compiles, passes every behavioural test, and only shows up as two people who cannot log in.
 */
class IdentifierUniquenessTest {

    private static List<Index> indexesOf(Class<?> type) {
        return Arrays.asList(type.getAnnotationsByType(Index.class));
    }

    private static Index named(Class<?> type, String indexName) {
        return indexesOf(type).stream()
                .filter(index -> index.indexName().equals(indexName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        type.getSimpleName() + " has no index named " + indexName
                                + " — see this test's javadoc for why it has to exist."));
    }

    @Test
    void loginIdentifiersAreUnique() {
        Index email = named(UserIdentity.class, "uk_user_identity_login_email");
        assertThat(email.unique()).isTrue();
        assertThat(email.fields()).containsExactly("loginEmail");

        Index mobile = named(UserIdentity.class, "uk_user_identity_login_mobile");
        assertThat(mobile.unique()).isTrue();
        assertThat(mobile.fields()).containsExactly("loginMobile");
    }

    @Test
    void theWorkEmailIsUniqueWithinATenant_notGlobally() {
        Index email = named(UserAccount.class, "uk_user_account_tenant_email");
        assertThat(email.unique()).isTrue();
        // Order matters: (tenantId, email), so the index also serves a lookup within one tenant.
        assertThat(email.fields()).containsExactly("tenantId", "email");

        // The global one must be GONE, not merely joined by the narrow one — leaving both would
        // keep refusing exactly the cross-tenant case the narrowing exists to allow.
        assertThat(indexesOf(UserAccount.class))
                .noneMatch(index -> index.indexName().equals("uk_user_account_email"));
    }

    @Test
    void theWorkMobileCarriesNoUniqueIndexAtAll() {
        // Deliberate: a shared work number is an ordinary contact. Uniqueness belongs on the login
        // identifier, which is a different column with a different job.
        assertThat(indexesOf(UserAccount.class))
                .filteredOn(Index::unique)
                .noneMatch(index -> Arrays.asList(index.fields()).contains("mobile"));
    }

    @Test
    void onePersonHoldsAtMostOneMembershipPerTenant() {
        // The premise reviveMembership relies on: re-hire reuses the closed row rather than
        // inserting a second one, and the database is what makes that true.
        Index membership = named(UserAccount.class, "uk_user_account_tenant_profile");
        assertThat(membership.unique()).isTrue();
        assertThat(membership.fields()).containsExactly("tenantId", "profileId");
    }
}
