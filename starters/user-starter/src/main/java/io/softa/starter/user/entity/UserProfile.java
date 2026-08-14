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
// No unique indexes on the login identifiers YET, deliberately. Nothing reads them this
// release (login still resolves the account by its email), so an index could only hurt: today
// an admin can clear a leaver's account email and reuse it for a new hire, and a unique index
// here would make that hire's profile creation fail on the leaver's stale identifier. The
// release that starts resolving people by identifier dedupes first, then adds the indexes.
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
            description = "Reserved for the lockout feature: when set and in the future, PASSWORD "
                    + "login will be refused until then. NOT ENFORCED YET — no code writes or reads "
                    + "it in this release; the column exists so the lockout release ships without "
                    + "another schema change. On the person, so switching company will buy no extra "
                    + "attempts")
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
