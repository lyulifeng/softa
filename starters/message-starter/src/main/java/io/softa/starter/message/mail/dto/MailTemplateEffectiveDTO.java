package io.softa.starter.message.mail.dto;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.enums.BodyMode;
import io.softa.starter.message.mail.enums.MailTemplateScope;

/**
 * One row of the tenant-facing <b>effective</b> template list: the caller's
 * own templates plus the platform templates they inherit, one row per
 * {@code code} (an own row shadows the platform row with the same code).
 * <p>
 * This is the management view, not send-time resolution: a CUSTOMIZED row is
 * shown even when it is disabled or locked out by the platform template
 * ({@code overridable = false}) — the {@code overridable} field here always
 * carries the <i>governing</i> platform value so the UI can badge a
 * customization that will never take effect.
 */
@Data
@Schema(name = "MailTemplateEffectiveDTO")
public class MailTemplateEffectiveDTO {

    public static MailTemplateEffectiveDTO from(MailTemplate template, MailTemplateScope scope,
                                                boolean overridable) {
        MailTemplateEffectiveDTO dto = new MailTemplateEffectiveDTO();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setSubject(template.getSubject());
        dto.setBodyMode(template.getBodyMode());
        dto.setIsEnabled(template.getIsEnabled());
        dto.setScope(scope);
        dto.setOverridable(overridable);
        dto.setUpdatedTime(template.getUpdatedTime());
        return dto;
    }

    @Schema(description = "Row id — for INHERITED rows this is the platform row's id (pass it to the "
            + "Customize action); for CUSTOMIZED/OWN rows the caller's own row (open the editor with it)")
    private Long id;

    @Schema(description = "Template code — the overlay key")
    private String code;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Subject template")
    private String subject;

    @Schema(description = "Body shape")
    private BodyMode bodyMode;

    @Schema(description = "Whether the shown row is active")
    private Boolean isEnabled;

    @Schema(description = "INHERITED = platform row, read-only, customizable; "
            + "CUSTOMIZED = own row shadowing a platform code (deletable to revert); "
            + "OWN = own row with a tenant-only code")
    private MailTemplateScope scope;

    @Schema(description = "Governing overridable flag of the code's platform template (true when no "
            + "platform template exists). INHERITED + false = Customize is unavailable; "
            + "CUSTOMIZED + false = this customization is ignored at send time (legacy row)")
    private Boolean overridable;

    @Schema(description = "Last update time of the shown row")
    private LocalDateTime updatedTime;
}
