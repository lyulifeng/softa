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
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.user.enums.Gender;
import io.softa.starter.user.enums.UserLayoutDensity;

/**
 * UserProfile Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "User Profile",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
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

    @Field(label = "Full Name", length = 64)
    private String fullName;

    @Field(label = "Chinese Name", length = 64)
    private String chineseName;

    @Field(label = "Birth Date")
    private LocalDate birthDate;

    @Field(label = "Birth Time")
    private LocalTime birthTime;

    @Field(label = "Birth City", length = 64)
    private String birthCity;

    @Field(label = "Gender")
    private Gender gender;

    @Field(label = "Profile Photo File ID")
    private Long photoId;

    @Field(label = "Language")
    private Language language;

    @Field(label = "Timezone")
    private Timezone timezone;

    @Field(label = "Density")
    private UserLayoutDensity density;
}
