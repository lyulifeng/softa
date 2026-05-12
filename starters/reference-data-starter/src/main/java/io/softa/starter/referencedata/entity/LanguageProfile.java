package io.softa.starter.referencedata.entity;

import java.io.Serial;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.base.enums.Language;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * Per-language formatting profile (date / time / decimal / thousand separators)
 * plus the runtime enable flag.
 *
 * <p>This is <em>not</em> a pure standards catalog — it mixes the language
 * identifier ({@link #language}, {@link #name}) with locale-flavoured formatting
 * conventions that tenants may legitimately want to override (e.g. a Brazilian
 * tenant displaying {@code 1.234,56} while another tenant on the same Portuguese
 * language prefers {@code 1,234.56}). Hence the table is tenant-scoped using
 * the platform-default + sparse-override pattern documented on
 * {@code SmsProviderRegion}:
 * <ul>
 *   <li>{@code tenant_id = 0} (or {@code NULL}) — platform default row, shared
 *       by all tenants that don't override.</li>
 *   <li>{@code tenant_id > 0} — per-tenant override of a specific language
 *       profile. Tenants only need to insert rows for the languages they
 *       actually want to deviate from the platform default.</li>
 * </ul>
 *
 * <p>{@code i18n} translation tables (e.g. {@code sys_field_trans},
 * {@code sys_option_item_trans}, {@code design_*_trans}) reference this row by
 * the {@link #language} string — concept FK, no relational constraint.
 */
@Data
@Schema(name = "LanguageProfile")
@EqualsAndHashCode(callSuper = true)
public class LanguageProfile extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Tenant ID — '0' / null for the platform-default row, "
            + "a specific tenant id for an override row.")
    private String tenantId;

    @Schema(description = "Language Name")
    private String name;

    @Schema(description = "Language (BCP-47); validated against the framework "
            + "Language enum at the metadata layer.")
    private Language language;

    @Schema(description = "Date Format")
    private String dateFormat;

    @Schema(description = "Time Format")
    private String timeFormat;

    @Schema(description = "Decimal Separator")
    private String decimalSeparator;

    @Schema(description = "Thousand Separator")
    private String thousandSeparator;

    @Schema(description = "Active")
    private Boolean active;
}
