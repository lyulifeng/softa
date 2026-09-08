package io.softa.starter.file.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.file.enums.DocumentTemplateType;

/**
 * DocumentTemplate Model — the body of a document a tenant sends out.
 *
 * <p><b>Tenant-isolated.</b> It carries {@code tenantId} and always did, but without the flag the
 * column was decoration: {@code fillTenantFieldForInsert} only stamps a model
 * {@code isMultiTenantControl} says is isolated, and {@code WhereBuilder} only narrows one. So every
 * row was written with a null tenant and every tenant read every other tenant's contract wording —
 * the document body, which is the part with names and salaries in it.
 *
 * <p>Everything around it in this package is already isolated ({@code ImportTemplate},
 * {@code ExportTemplate}, {@code SigningRequest}, {@code SigningDocument}), including this model's
 * own child {@link DocumentTemplateSignSlot}. A parent that is shared while its sign slots are
 * per-tenant is not a design, so this is a fix rather than a change of scope.
 *
 * <p>⚠️ Existing rows have {@code tenant_id IS NULL} and become invisible to everyone the moment the
 * flag lands — document generation reads the body through {@code searchList("DocumentTemplate", …)}
 * and would fail with "HTML template not found". The column has to be backfilled before the patched
 * binary starts; downstream apps own that migration because only they know who each row belongs to.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(multiTenant = true)
public class DocumentTemplate extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field
    private String modelName;

    @Field(length = 128)
    private String fileName;

    @Field
    private DocumentTemplateType templateType;

    @Field(label = "File Template ID")
    private Long fileId;

    @Field(label = "HTML Template Content", fieldType = FieldType.TEXT)
    private String htmlTemplate;

    @Field(label = "Convert To PDF")
    private Boolean convertToPdf;

    @Field(length = 256)
    private String description;
}
