package io.softa.starter.user.service.impl;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PRD D4, rule by rule. The four failure strings are asserted verbatim because the screen renders
 * a four-item checklist from them — a reworded message silently breaks the mapping.
 */
class PasswordPolicyTest {

    private static final String MOBILE = "+8613800135678";
    private static final String EMAIL = "alice@acme.com";

    @Test
    void aCompliantPasswordPasses() {
        assertThatCode(() -> PasswordPolicy.validate("Str0ngPass", MOBILE, EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    void specialCharactersAreAllowedButNotRequired() {
        // D4 chose not to require them: mandating specials pushes people towards predictable
        // substitutions. Both shapes must pass.
        assertThat(PasswordPolicy.check("Str0ngPass", MOBILE, EMAIL)).isEmpty();
        assertThat(PasswordPolicy.check("Str0ng!Pass#", MOBILE, EMAIL)).isEmpty();
    }

    @Test
    void lengthBounds() {
        assertThat(PasswordPolicy.check("Sh0rtPw", MOBILE, EMAIL)).contains("8–32 characters");
        assertThat(PasswordPolicy.check("A1" + "x".repeat(31), MOBILE, EMAIL))
                .contains("8–32 characters");
        // Exactly at the bounds is acceptable.
        assertThat(PasswordPolicy.check("Str0ngPw", MOBILE, EMAIL)).isEmpty();
        assertThat(PasswordPolicy.check("Str0ng" + "x".repeat(26), MOBILE, EMAIL)).isEmpty();
    }

    @Test
    void needsBothCases() {
        assertThat(PasswordPolicy.check("str0ngpass", MOBILE, EMAIL))
                .contains("contains uppercase and lowercase letters");
        assertThat(PasswordPolicy.check("STR0NGPASS", MOBILE, EMAIL))
                .contains("contains uppercase and lowercase letters");
    }

    @Test
    void needsADigit() {
        assertThat(PasswordPolicy.check("StrongPass", MOBILE, EMAIL)).contains("contains numbers");
    }

    @Test
    void rejectsWhitespace() {
        assertThat(PasswordPolicy.check("Str0ng Pass", MOBILE, EMAIL))
                .contains("no spaces or parts of your phone / email");
    }

    // ── 规则 4:不得由本人联系方式派生 ───────────────────────────────────

    @Test
    void rejectsTheMobileTail() {
        // The guesses an attacker who knows the target tries FIRST. Length and character-class
        // rules bound the search space; this one removes its most likely corner.
        assertThat(PasswordPolicy.check("Str0ng5678", MOBILE, EMAIL))
                .contains("no spaces or parts of your phone / email");
    }

    @Test
    void rejectsTheEmailLocalPart_caseInsensitively() {
        // An attacker trying "alice" tries "Alice" too, so matching only exact case would make
        // the rule decorative.
        assertThat(PasswordPolicy.check("Alice12345", MOBILE, EMAIL))
                .contains("no spaces or parts of your phone / email");
    }

    @Test
    void ignoresAVeryShortEmailLocalPart() {
        // "hr@company.com" would otherwise reject every password containing "hr" — far too much
        // to be a useful constraint.
        assertThat(PasswordPolicy.check("Chr0nograph", null, "hr@company.com")).isEmpty();
    }

    @Test
    void toleratesMissingContactDetails() {
        // A person may have only one identifier, or none yet at set-password time.
        assertThat(PasswordPolicy.check("Str0ngPass", null, null)).isEmpty();
    }

    @Test
    void reportsEveryUnmetRuleAtOnce() {
        // The screen shows four checklist items; returning only the first failure would turn
        // setting a password into a guessing game.
        assertThat(PasswordPolicy.check("abc", null, null)).hasSize(3)
                .containsExactly("8–32 characters",
                        "contains uppercase and lowercase letters",
                        "contains numbers");
    }

    @Test
    void nullIsRejectedAsTooShort() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("8–32 characters");
    }
}
