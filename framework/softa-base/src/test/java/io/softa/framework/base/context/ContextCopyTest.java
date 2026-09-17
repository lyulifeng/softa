package io.softa.framework.base.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * {@code copy()} is how every isolated read is made — a scope waiver, a cleared selection — and a field
 * it forgets is silently absent on that copy. For the company / country sets that means the narrowing
 * reads "unknown" and does nothing, on exactly the reads that were meant to stay narrowed.
 */
class ContextCopyTest {

    @Test
    void copyCarriesTheAccessibleCompaniesAndCountries() {
        Context original = new Context();
        original.setUserId(1L);
        original.setAccessibleCompanyIds(Set.of(11L, 12L));
        original.setAccessibleCountries(Set.of("SG", "NZ"));

        Context copy = original.copy();

        assertThat(copy.getAccessibleCompanyIds()).containsExactlyInAnyOrder(11L, 12L);
        assertThat(copy.getAccessibleCountries()).containsExactlyInAnyOrder("SG", "NZ");
    }

    @Test
    void copyKeepsUnknownAsNull() {
        Context copy = new Context().copy();
        assertThat(copy.getAccessibleCompanyIds()).isNull();
        assertThat(copy.getAccessibleCountries()).isNull();
    }
}
