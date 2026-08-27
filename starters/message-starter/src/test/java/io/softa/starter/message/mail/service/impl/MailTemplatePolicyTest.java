package io.softa.starter.message.mail.service.impl;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.message.MailScope;
import io.softa.framework.orm.dto.FileInfo;
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.enums.BodyMode;
import io.softa.starter.message.mail.enums.MailPriority;
import io.softa.starter.message.mail.service.impl.MailTemplateServiceImpl.ScopePair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure overlay policy: {@link MailTemplateServiceImpl#pickForSend} (which
 * tier's row a send uses), {@link MailTemplateServiceImpl.ScopePair} (slotting
 * of visible rows), and {@link MailTemplateServiceImpl#copyForTenant} (the
 * Customize content copy).
 */
class MailTemplatePolicyTest {

    private static MailTemplate template(Long tenantId, Boolean enabled, Boolean overridable) {
        MailTemplate t = new MailTemplate();
        t.setTenantId(tenantId);
        t.setCode("CODE");
        t.setIsEnabled(enabled);
        t.setOverridable(overridable);
        return t;
    }

    @Nested
    class PickForSend {

        private final MailTemplate platform = template(0L, true, null);
        private final MailTemplate own = template(5L, true, null);

        @Test
        void overlayPrefersTheTenantRow() {
            assertSame(own, MailTemplateServiceImpl
                    .pickForSend(platform, own, MailScope.OVERLAY, true).orElseThrow());
        }

        @Test
        void overlayFallsBackToThePlatformRow() {
            assertSame(platform, MailTemplateServiceImpl
                    .pickForSend(platform, null, MailScope.OVERLAY, true).orElseThrow());
        }

        @Test
        void aDisabledTenantRowFallsBackToThePlatformRow() {
            MailTemplate disabledOwn = template(5L, false, null);
            assertSame(platform, MailTemplateServiceImpl
                    .pickForSend(platform, disabledOwn, MailScope.OVERLAY, true).orElseThrow());
        }

        @Test
        void aLockedPlatformRowBeatsTheTenantOverride() {
            MailTemplate locked = template(0L, true, false);
            assertSame(locked, MailTemplateServiceImpl
                    .pickForSend(locked, own, MailScope.OVERLAY, true).orElseThrow());
        }

        @Test
        void aLockedDisabledPlatformRowResolvesToNothing_neverToTheShadowedTenantRow() {
            MailTemplate lockedDisabled = template(0L, false, false);
            assertTrue(MailTemplateServiceImpl
                    .pickForSend(lockedDisabled, own, MailScope.OVERLAY, true).isEmpty());
        }

        @Test
        void platformOnlyIgnoresTheTenantRow() {
            assertSame(platform, MailTemplateServiceImpl
                    .pickForSend(platform, own, MailScope.PLATFORM_ONLY, true).orElseThrow());
        }

        @Test
        void platformOnlyWithNoPlatformRowResolvesToNothing() {
            assertTrue(MailTemplateServiceImpl
                    .pickForSend(null, own, MailScope.PLATFORM_ONLY, true).isEmpty());
        }

        @Test
        void withoutEnabledFilterDisabledRowsStillResolve() {
            MailTemplate disabledOwn = template(5L, false, null);
            // resolveAny semantics: authoring tools inspect disabled templates.
            Optional<MailTemplate> picked = MailTemplateServiceImpl
                    .pickForSend(platform, disabledOwn, MailScope.OVERLAY, false);
            assertSame(disabledOwn, picked.orElseThrow());
        }

        @Test
        void nullEnabledCountsAsDisabledOnDeliveryPaths() {
            MailTemplate nullEnabledOwn = template(5L, null, null);
            assertSame(platform, MailTemplateServiceImpl
                    .pickForSend(platform, nullEnabledOwn, MailScope.OVERLAY, true).orElseThrow());
        }
    }

    @Nested
    class Slotting {

        @Test
        void platformRowsAlwaysLandInThePlatformSlot_evenForThePlatformCaller() {
            MailTemplate platformRow = template(0L, true, null);
            ScopePair pair = ScopePair.of(List.of(platformRow), 0L);
            assertSame(platformRow, pair.platform());
            assertNull(pair.own());
        }

        @Test
        void tenantCallerGetsBothSlots() {
            MailTemplate platformRow = template(0L, true, null);
            MailTemplate ownRow = template(5L, true, null);
            ScopePair pair = ScopePair.of(List.of(platformRow, ownRow), 5L);
            assertSame(platformRow, pair.platform());
            assertSame(ownRow, pair.own());
        }

        @Test
        void aNullTenantIdRowCountsAsPlatform() {
            MailTemplate legacyRow = template(null, true, null);
            ScopePair pair = ScopePair.of(List.of(legacyRow), 5L);
            assertSame(legacyRow, pair.platform());
            assertNull(pair.own());
        }
    }

    @Nested
    class CopyForTenant {

        @Test
        void carriesContentAndDefaults_leavesIdentityAndPolicyBehind() {
            MailTemplate platform = template(0L, true, true);
            platform.setId(77L);
            platform.setName("Welcome");
            platform.setDescription("desc");
            platform.setSubject("Hi {{ name }}");
            platform.setBodyHtml("<p>hi</p>");
            platform.setBodyText("hi");
            platform.setBodyMode(BodyMode.HTML_WITH_AUTHORED_PLAIN);
            platform.setDefaultPriority(MailPriority.HIGH);
            platform.setReplyTo("reply@example.com");
            platform.setAttachments(List.of(new FileInfo()));
            platform.setPreferredServerConfigId(99L);

            MailTemplate copy = MailTemplateServiceImpl.copyForTenant(platform);

            assertEquals("CODE", copy.getCode());
            assertEquals("Welcome", copy.getName());
            assertEquals("desc", copy.getDescription());
            assertEquals("Hi {{ name }}", copy.getSubject());
            assertEquals("<p>hi</p>", copy.getBodyHtml());
            assertEquals("hi", copy.getBodyText());
            assertEquals(BodyMode.HTML_WITH_AUTHORED_PLAIN, copy.getBodyMode());
            assertEquals(true, copy.getIsEnabled());
            assertEquals(MailPriority.HIGH, copy.getDefaultPriority());
            assertEquals("reply@example.com", copy.getReplyTo());
            assertEquals(1, copy.getAttachments().size());
            assertEquals(99L, copy.getPreferredServerConfigId());
            // Identity is minted by the create path; overridable is platform-row policy.
            assertNull(copy.getId());
            assertNull(copy.getTenantId());
            assertNull(copy.getOverridable());
        }
    }
}
