package io.softa.starter.user.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.user.enums.LoginMethod;

/**
 * UserSecurityPolicy Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "User Security Policy",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class UserSecurityPolicy extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "Policy Name", length = 64)
    private String name;

    @Field(label = "Policy Code", length = 64)
    private String code;

    @Field(label = "Login Methods")
    private List<LoginMethod> loginMethods;

    @Field(label = "Active Device Limit")
    private Integer activeDeviceLimit;

    @Field(label = "Server Session Duration")
    private Integer sessionDuration;

    @Field(label = "Client Cookie-Session Idle Duration")
    private Integer sessionIdleDuration;

    @Field(label = "Force Change Initial Password")
    private Boolean forceChangeInitialPassword;

    @Field(label = "Password Valid Days")
    private Integer passwordValidDays;

    @Field(label = "Password Retry Interval")
    private Integer passwordRetryInterval;

    @Field(label = "Password Retry Limit")
    private Integer passwordRetryLimit;

    @Field(label = "Password Complexity Prompt", length = 128)
    private String passwordComplexityPrompt;

    @Field(label = "Passwords Not Duplicate")
    private Integer passwordNotDuplicate;

    @Field(label = "Minimum Character Length")
    private Integer minLength;

    @Field(label = "Minimum Lowercase Characters")
    private Integer minLowercase;

    @Field(label = "Minimum Uppercase Characters")
    private Integer minUppercase;

    @Field(label = "Minimum Digits")
    private Integer minDigits;

    @Field(label = "Minimum Modified Characters")
    private Integer minModifiedChars;

    @Field(label = "Minimum Special Characters")
    private Integer minSpecialChars;

    @Field(label = "Active")
    private Boolean active;
}
