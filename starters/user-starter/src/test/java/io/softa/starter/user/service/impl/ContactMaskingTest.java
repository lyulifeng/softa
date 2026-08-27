package io.softa.starter.user.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Masking on the /join screens has two jobs at once: the invitee must RECOGNISE their own address
 * (or they cannot tell a mis-addressed invitation from their own), while a leaked link must not
 * hand a stranger a working phone number or email. These tests pin both halves.
 */
class ContactMaskingTest {

    @Test
    void mobileKeepsEnoughToRecognise_andNotEnoughToDial() {
        assertThat(ContactMasking.mobile("+8613800138000")).isEqualTo("861****8000");
        // Recognisable: the owner knows their last four. Unusable: the middle is gone.
        assertThat(ContactMasking.mobile("+8613800138000")).doesNotContain("13800138000");
    }

    @Test
    void mobileStripsFormatting() {
        assertThat(ContactMasking.mobile("+86 138-0013-8000")).isEqualTo("861****8000");
    }

    @Test
    void shortNumbersKeepLess() {
        // Masking a 6-digit number to the standard shape would expose proportionally more of it,
        // so short numbers surrender only their tail.
        assertThat(ContactMasking.mobile("123456")).isEqualTo("****456");
    }

    @Test
    void emailKeepsTheDomain() {
        // The domain stays because it is what tells the invitee WHICH company invited them —
        // that is the recognition the screen is for.
        assertThat(ContactMasking.email("alice@acme.com")).isEqualTo("a***@acme.com");
    }

    @Test
    void malformedEmailRevealsNothing() {
        assertThat(ContactMasking.email("not-an-email")).isEqualTo("***");
    }

    @Test
    void blankInputsMaskToNull() {
        // null rather than "***": the screen distinguishes "no such channel" (hide the option)
        // from "a channel we are hiding the value of".
        assertThat(ContactMasking.mobile(null)).isNull();
        assertThat(ContactMasking.mobile("  ")).isNull();
        assertThat(ContactMasking.email(null)).isNull();
    }
}
