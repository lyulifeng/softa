package io.softa.starter.studio.release.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.studio.release.enums.DesignAppEnvStatus;
import io.softa.starter.studio.release.enums.DesignAppEnvType;

/**
 * DesignAppEnv Model — represents a deployment environment for a DesignApp.
 * <p>
 * Each Env tracks its own version state via {@code currentVersionId}, which points to
 * the latest version that has been successfully deployed to this environment.
 * When deploying a new Version, the system merges released versions in the
 * sealedTime interval {@code (currentVersionId, targetVersion]} to produce the Deployment.
 * <p>
 * Concurrent deployments against the same env are serialized via {@code envStatus}.
 * A deployment may only start when {@code envStatus == STABLE}; it acquires the lock
 * by compare-and-set transitioning the field to {@code DEPLOYING} and releases it on
 * completion (success or failure).
 * <p>
 * Authentication between Studio and the runtime targeted by this Env uses per-env
 * Ed25519 keypairs. Studio signs outgoing upgrade requests with {@code privateKey};
 * the runtime verifies against the corresponding {@code publicKey}. Rotation scope
 * is one env at a time — reissuing a keypair here does not disturb other runtimes.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design App Env",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class DesignAppEnv extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID", required = true)
    private Long appId;

    @Field(label = "Current Version")
    private Long currentVersionId;

    @Field(label = "Last Deployment", description = "Most recent deployment for this env, regardless of outcome")
    private Long lastDeploymentId;

    @Field(label = "Env Status")
    private DesignAppEnvStatus envStatus;

    @Field(label = "Env Name", required = true, length = 64)
    private String name;

    @Field(label = "Sequence")
    private Integer sequence;

    @Field(label = "Env Type")
    private DesignAppEnvType envType;

    @Field(label = "Protected Env")
    private Boolean protectedEnv;

    @Field(label = "Active")
    private Boolean active;

    @Field(label = "Upgrade API EndPoint", required = true, length = 128)
    private String upgradeEndpoint;

    @Field(label = "Public Key", length = 256)
    private String publicKey;

    @Field(label = "Private Key", length = 512)
    private String privateKey;

    @Field(label = "Auto Upgrade")
    private Boolean autoUpgrade;

    @Field(label = "Auto Execute DDL")
    private Boolean autoExecuteDDL;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Deleted")
    private Boolean deleted;

    @Field(label = "Version")
    private Integer version;
}
