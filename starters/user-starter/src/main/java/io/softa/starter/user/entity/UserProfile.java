package io.softa.starter.user.entity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.base.enums.Language;
import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.user.enums.Gender;
import io.softa.starter.user.enums.UserLayoutDensity;

/**
 * UserProfile Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        multiTenant = true,
        searchName = {"fullName"}
)
public class UserProfile extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "User ID", required = true)
    private Long userId;

    @Field
    private String fullName;

    @Field
    private String chineseName;

    @Field
    private LocalDate birthDate;

    @Field
    private LocalTime birthTime;

    @Field
    private String birthCity;

    @Field
    private Gender gender;

    @Field(label = "Profile Photo File ID", fieldType = FieldType.FILE)
    private Long photoId;

    @Field
    private Language language;

    @Field
    private Timezone timezone;

    @Field
    private UserLayoutDensity density;
}
