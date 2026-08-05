package io.softa.starter.tenant.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * i18n translations of {@link TenantOptionItem} display attributes, keyed by
 * {@code rowId} + {@code languageCode}.
 * <p>
 * Not multi-tenant itself: {@code rowId} already points at a tenant-scoped row, so isolation
 * follows the parent rather than being repeated here (same shape as {@code SysOptionItemTrans}).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Tenant Option Item Translation",
        businessKey = {"rowId", "languageCode"},
        description = "Translations for tenant_option_item"
)
public class TenantOptionItemTrans extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(required = true)
    private String languageCode;

    @Field(label = "Row ID", required = true)
    private Long rowId;

    @Field
    private String label;

    @Field(length = 512)
    private String description;
}
