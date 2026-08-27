package io.softa.starter.user.service.impl;

import org.junit.jupiter.api.Test;

import io.softa.starter.user.dto.JoinContacts;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The membership test behind {@code setJoinPassword}'s first authorization check.
 *
 * <p>Worth its own test because that check is the only thing standing between "holds an invitation
 * link" and "can set a password on an arbitrary profileId". If {@code includes} ever answered true
 * for something the invitation does not name, a link-holder could overwrite a stranger's password.
 */
class JoinContactsTest {

    @Test
    void matchesEitherChannel() {
        JoinContacts contacts = new JoinContacts("alice@acme.com", "+8613800138000");
        assertThat(contacts.includes("alice@acme.com")).isTrue();
        assertThat(contacts.includes("+8613800138000")).isTrue();
    }

    @Test
    void ignoresCaseOnEmail() {
        // Addresses are stored as typed but compared as identifiers: a person who set their login
        // email as Alice@acme.com must still match the invitation addressed to alice@acme.com.
        assertThat(new JoinContacts("alice@acme.com", null).includes("ALICE@ACME.COM")).isTrue();
    }

    @Test
    void rejectsAnythingElse() {
        JoinContacts contacts = new JoinContacts("alice@acme.com", "+8613800138000");
        assertThat(contacts.includes("bob@acme.com")).isFalse();
        assertThat(contacts.includes("+8613800138001")).isFalse();
        // Substrings must not match either — otherwise a prefix would be enough to pass the gate.
        assertThat(contacts.includes("alice")).isFalse();
        assertThat(contacts.includes("+86138")).isFalse();
    }

    @Test
    void nullNeverMatches_evenAgainstAnAbsentChannel() {
        // An invitation with no mobile must not accept a profile whose loginMobile is also null:
        // two absent values are not a proof of identity. This is the case that would otherwise
        // let ANY profile with one empty channel through the check.
        JoinContacts emailOnly = new JoinContacts("alice@acme.com", null);
        assertThat(emailOnly.includes(null)).isFalse();
        assertThat(new JoinContacts(null, null).includes(null)).isFalse();
    }
}
