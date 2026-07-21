package io.softa.starter.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for {@code POST /login/sendEmailCode} — the email to send a login code to. */
@Data
public class SendEmailCodeDTO {
    @Email(message = "Email format is incorrect!")
    @NotBlank(message = "Email cannot be empty!")
    private String email;
}
