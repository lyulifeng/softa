package io.softa.starter.user.dto;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The password form has an Email tab and a Mobile tab, and both post to the same endpoint. The DTO
 * therefore constrains the identifier only to "present": an email-format check on it turned every
 * mobile password login into a 400 before the service ever saw it.
 */
class IdentifierPasswordDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static IdentifierPasswordDTO body(String identifier, String password) {
        IdentifierPasswordDTO dto = new IdentifierPasswordDTO();
        dto.setIdentifier(identifier);
        dto.setPassword(password);
        return dto;
    }

    @Test
    void aMobileNumber_isALegalIdentifier() {
        Set<ConstraintViolation<IdentifierPasswordDTO>> violations =
                validator.validate(body("+6591234567", "secret"));

        assertThat(violations).isEmpty();
    }

    @Test
    void anEmail_isStillALegalIdentifier() {
        assertThat(validator.validate(body("alice@acme.com", "secret"))).isEmpty();
    }

    @Test
    void aBlankIdentifierOrPassword_isRefused() {
        assertThat(validator.validate(body(" ", "secret")))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("identifier");
        assertThat(validator.validate(body("alice@acme.com", "")))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("password");
    }
}
