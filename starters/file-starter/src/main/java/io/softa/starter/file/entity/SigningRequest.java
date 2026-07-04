package io.softa.starter.file.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.file.enums.SigningRequestStatus;

/**
 * SigningRequest Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(copyable = false)
public class SigningRequest extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID", length = 32)
    private String tenantId;

    @Field
    private String modelName;

    @Field(length = 256)
    private String title;

    @Field
    private String code;

    @Field
    private SigningRequestStatus status;

    @Field(label = "Recipient User")
    private Long recipient;

    @Field
    private LocalDateTime expiresTime;

    @Field(fieldType = FieldType.ONE_TO_MANY, relatedField = "signingRequestId")
    private List<SigningDocument> signingDocuments;
}
