package io.softa.starter.message.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One template token surfaced to variable-input UIs (Send Test / Preview
 * dialogs): the client renders {@code VARIABLE} as a text input,
 * {@code COLLECTION} as a JSON-value input (loop data), and shows
 * {@code EXPRESSION} / {@code RESERVED_FIELD} as informational rows
 * (expressions need operands supplied as raw JSON; reserved fields resolve
 * server-side).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "MailTemplateVariable")
public class MailTemplateVariableDTO {

    @Schema(description = "Token as written in the template, e.g. `name`, `user.name`, or a loop collection")
    private String name;

    @Schema(description = "Token kind: VARIABLE / COLLECTION / EXPRESSION / RESERVED_FIELD")
    private TemplateVariableKind kind;
}
