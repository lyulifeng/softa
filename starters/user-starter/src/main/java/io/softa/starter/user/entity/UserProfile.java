package io.softa.starter.user.entity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.base.enums.Language;
import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
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
/**
 * The PERSON. One row per human being, shared across every company they work for.
 *
 * <p>Deliberately NOT multi-tenant: a person is not owned by a company. Their credentials live
 * here — someone employed by two companies has one password, and leaving one company must not
 * affect their ability to log in to the other. {@link UserAccount} is the per-company membership.
 */
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        searchName = {"fullName"}
)
@Index(indexName = "uk_user_profile_login_email", fields = {"loginEmail"}, unique = true,
        message = "This email is already in use.")
@Index(indexName = "uk_user_profile_login_mobile", fields = {"loginMobile"}, unique = true,
        message = "This mobile number is already in use.")
public class UserProfile extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    /**
     * DEPRECATED back-reference. {@link UserAccount#getProfileId()} is the relation now; this is
     * kept only so the data migration can map the old 1:1 pairing, and so a rolled-back binary
     * still finds its rows. Not required any more — a person may exist before any membership does.
     */
    @Field(label = "User ID")
    private Long userId;

    // ── Login credentials: they belong to the person, not to any one company ──────────────

    @Field(description = "Login identifier. Seeded from the account's work email when a person is "
            + "created; globally unique because it identifies a human, not an employment")
    private String loginEmail;

    @Field(description = "Login identifier, dial-code format. Globally unique, same reasoning as "
            + "loginEmail")
    private String loginMobile;

    @Field(copyable = false, length = 256,
            description = "Password hash. length mirrors the column this moved from: the stored "
                    + "hash is 128 characters, so the 64-char type default would silently truncate "
                    + "it and no credential could be written at all")
    private String password;

    @Field(copyable = false, description = "Per-row salt for the password hash")
    private String passwordSalt;

    @Field(copyable = false,
            description = "When set and in the future, PASSWORD login is refused until then. On the "
                    + "person, so switching company buys no extra attempts; a verification code "
                    + "stays available throughout — what is locked is the password, not the person")
    private LocalDateTime passwordLockedUntil;

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
