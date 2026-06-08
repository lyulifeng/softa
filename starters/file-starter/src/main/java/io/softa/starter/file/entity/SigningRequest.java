package io.softa.starter.file.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.file.enums.SigningRequestStatus;

/**
 * SigningRequest Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Signing Request")
public class SigningRequest extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID", length = 32)
    private String tenantId;

    @Field(label = "Model Name", length = 64)
    private String modelName;

    @Field(label = "Title", length = 256)
    private String title;

    @Field(label = "Code", length = 64)
    private String code;

    @Field(label = "Status")
    private SigningRequestStatus status;

    @Field(label = "Recipient User")
    private Long recipient;

    @Field(label = "Expires Time")
    private LocalDateTime expiresTime;

    @Field(label = "Signing Documents")
    private List<SigningDocument> signingDocuments;
}
