package io.softa.starter.user.dto;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.context.UserInfo;
import io.softa.starter.user.enums.AccountStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The client branches on {@code resolved} to decide between "session issued" and "show the
 * picker". That name is part of the wire contract, so it is asserted here rather than left to the
 * {@code isX} bean convention that happens to produce it today.
 */
class AuthenticationResultJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aResolvedResult_saysResolvedOnTheWire() throws Exception {
        String json = mapper.writeValueAsString(
                AuthenticationResult.resolved(7L, new UserInfo(), false));

        assertThat(json).contains("\"resolved\":true");
    }

    @Test
    void aPendingChoice_saysNotResolved() throws Exception {
        String json = mapper.writeValueAsString(AuthenticationResult.choicePending(7L,
                List.of(new MembershipOption(1L, 2L, "Acme", AccountStatus.ACTIVE, false)),
                false, "token"));

        assertThat(json).contains("\"resolved\":false").contains("\"authToken\":\"token\"");
    }
}
