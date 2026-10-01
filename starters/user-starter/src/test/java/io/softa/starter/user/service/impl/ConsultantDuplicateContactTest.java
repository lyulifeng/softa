package io.softa.starter.user.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.dto.ConsultantProfileDTO;
import io.softa.starter.user.entity.ConsultantProfile;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Creating a consultant with a login identifier that already belongs to another consultant is
 * refused — and the refusal names the channel.
 *
 * <p>The operator typed an email and a mobile. The old refusal, "that person is already a
 * consultant", was true and useless: it did not say which of the two boxes to change, and the one
 * thing the operator can act on is which field collided. The lookup used to resolve EITHER channel
 * to a person first and only then ask whether that person was a consultant, by which point the
 * channel was gone. Each channel is now asked on its own.
 */
class ConsultantDuplicateContactTest {

    private static final String EMAIL = "minir34124+cp1@bullbaby.com";
    private static final String MOBILE = "+6580000001";

    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final UserProfileService profileService = mock(UserProfileService.class);
    private final ConsultantServiceImpl service = spy(new ConsultantServiceImpl());

    {
        ReflectionTestUtils.setField(service, "identityService", identityService);
        ReflectionTestUtils.setField(service, "profileService", profileService);
    }

    private static UserIdentity heldBy(long profileId) {
        UserIdentity identity = new UserIdentity();
        ReflectionTestUtils.setField(identity, "profileId", profileId);
        return identity;
    }

    private static ConsultantProfileDTO createForm(String email, String mobile) {
        ConsultantProfileDTO form = new ConsultantProfileDTO();
        form.setUsername("QA-Consultant-tmp");
        form.setEmail(email);
        form.setMobile(mobile);
        return form;
    }

    /** Whoever the lookup finds is already a consultant. */
    private void everyFoundPersonIsAConsultant() {
        doReturn(Optional.of(new ConsultantProfile())).when(service).searchOne(any(Filters.class));
    }

    @Test
    void anEmailHeldByAnotherConsultantIsRefusedByName() {
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(heldBy(11L)));
        when(identityService.findByLoginIdentifier("+6580000099")).thenReturn(Optional.empty());
        everyFoundPersonIsAConsultant();

        assertThatThrownBy(() -> service.save(createForm(EMAIL, "+6580000099")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This email is already used by another consultant profile.");
    }

    @Test
    void aMobileHeldByAnotherConsultantIsRefusedByName() {
        when(identityService.findByLoginIdentifier("minir34124+cptmp@bullbaby.com")).thenReturn(Optional.empty());
        when(identityService.findByLoginIdentifier(MOBILE)).thenReturn(Optional.of(heldBy(11L)));
        everyFoundPersonIsAConsultant();

        assertThatThrownBy(() -> service.save(createForm("minir34124+cptmp@bullbaby.com", MOBILE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This mobile is already used by another consultant profile.");
    }

    /**
     * Both collide: the email is named. It is the channel the person lookup resolves first, so the
     * refusal matches what the save would otherwise have acted on — and fixing the email first is
     * the right order anyway, since the mobile may well belong to the same person.
     */
    @Test
    void collidingOnBothChannelsNamesTheEmail() {
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(heldBy(11L)));
        when(identityService.findByLoginIdentifier(MOBILE)).thenReturn(Optional.of(heldBy(12L)));
        everyFoundPersonIsAConsultant();

        assertThatThrownBy(() -> service.save(createForm(EMAIL, MOBILE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This email is already used by another consultant profile.");
    }
}
