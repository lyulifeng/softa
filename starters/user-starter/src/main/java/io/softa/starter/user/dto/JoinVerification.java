package io.softa.starter.user.dto;

/**
 * The result of proving identity on the /join flow.
 *
 * <p>Deliberately NOT an {@link AuthenticationResult}: no session is issued here and no company is
 * resolved, because at this point the person has verified a code but not yet agreed to join. A new
 * invitee has no active membership at all, so running the normal authentication tail would reject
 * them with "not linked to any company" — the very people the flow exists for.
 *
 * @param profileId       the person, found by the invitation's own address or created on the spot
 * @param mustSetPassword whether a password step must run before the confirm screen
 * @param proof           single-use evidence that the code was passed, which setJoinPassword and
 *                        confirmJoin require back — see {@code JoinProofGuard} for why a
 *                        caller-supplied profileId alone could not authorize either
 */
public record JoinVerification(Long profileId, boolean mustSetPassword, String proof) {
}
