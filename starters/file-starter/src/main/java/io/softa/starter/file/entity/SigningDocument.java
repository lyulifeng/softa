package io.softa.starter.file.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.file.enums.SigningDocumentStatus;

/**
 * SigningDocument Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(copyable = false)
public class SigningDocument extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID", length = 32)
    private String tenantId;

    @Field(label = "Signing Request ID")
    private Long signingRequestId;

    @Field(length = 256)
    private String title;

    @Field
    private Integer sequence;

    @Field
    private SigningDocumentStatus status;

    @Field(label = "Document Template")
    private Long templateId;

    @Field
    private String signSlotCode;

    @Field(label = "Signed Image File")
    private Long signedImageId;

    @Field(label = "Signed PDF File")
    private Long signedPdfId;

    @Field(label = "Signer User ID")
    private Long signerUserId;

    @Field(label = "Evidence ID")
    private String evidenceId;

    @Field(label = "Signature evidence JSON")
    private JsonNode signatureEvidence;

    @Field
    private LocalDateTime signedTime;
}
