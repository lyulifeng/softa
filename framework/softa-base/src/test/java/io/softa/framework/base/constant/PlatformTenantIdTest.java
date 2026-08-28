package io.softa.framework.base.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the platform tenant id, which looks like a free choice and is not.
 *
 * <p>Nothing else pins it. Every consumer references the constant, so the behavioural tests pass at
 * any value — they verify that a tenant's rows beat the platform's, not which id the platform holds.
 * The value only surfaces outside a unit test: the platform operator's session tenant has to BE this
 * id, or the mail server config they create through the ordinary UI is stamped with something the
 * fallback query never reads, and it quietly serves as nobody's fallback.
 *
 * <p>Two mistakes have each actually been made. The messaging tier and the platform tenant sat on
 * different ids (0 and -1) long enough for a broken platform template to survive months, because
 * nothing could reach it to fix it. And an earlier bootstrap seed used 0, which cannot work: this id
 * is resolved as a primary key, and the ORM screens 0 out of an id lookup before any SQL runs, so
 * the tenant row exists while nobody can log into it.
 */
class PlatformTenantIdTest {

    @Test
    void cannotBeZero() {
        // IdUtils.validId (softa-orm) treats 0 as "no id at all". Asserted here rather than by
        // calling it, because softa-base cannot depend on softa-orm — which is also why the rule is
        // easy to forget from this side.
        assertThat(BaseConstant.PLATFORM_TENANT_ID)
                .as("0 is filtered out of id lookups before any SQL runs, so a tenant seeded at 0 "
                        + "exists yet can never be resolved and nobody can log into it")
                .isNotEqualTo(0L);
    }

    @Test
    void cannotCollideWithARealTenant() {
        // CosID generates positive ids only, so the synthetic tenant has to live below zero.
        assertThat(BaseConstant.PLATFORM_TENANT_ID).isNegative();
    }

    @Test
    void matchesTheTenantTheBootstrapSeedCreates() {
        // Downstream bootstrap seeds hardcode tenant_info(-1), and environments that ran them
        // already live there. Changing this constant strands every one of them.
        assertThat(BaseConstant.PLATFORM_TENANT_ID).isEqualTo(-1L);
    }
}
