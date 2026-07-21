package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for {@code POST /login/sendMobileCode} — the mobile number to send a login code to. */
@Data
public class SendMobileCodeDTO {
    @NotBlank(message = "Mobile cannot be empty!")
    private String mobile;
}
