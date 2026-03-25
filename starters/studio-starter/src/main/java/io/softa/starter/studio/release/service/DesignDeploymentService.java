package io.softa.starter.studio.release.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.studio.release.dto.DeploymentPreviewDTO;
import io.softa.starter.studio.release.entity.DesignDeployment;

/**
 * DesignDeployment Model Service Interface.
 * <p>
 * A Deployment is the immutable deployment artifact produced when a Version is deployed to an Env.
 * It combines what was previously split between Release (content preparation) and Deployment (execution)
 * into a single self-contained record.
 * <p>
 * The deploy process:
 * <ol>
 *   <li>Validates the target version and environment</li>
 *   <li>Selects released versions in the sealedTime interval from env.currentVersionId to targetVersionId</li>
 *   <li>Merges version contents and generates DDL</li>
 *   <li>Creates a Deployment record with merged content, DDL, and execution status</li>
 *   <li>Executes the deployment (sync or async)</li>
 *   <li>Updates env.currentVersionId on success</li>
 *   <li>Auto-freezes the target version after successful PROD deployment</li>
 * </ol>
 */
public interface DesignDeploymentService extends EntityService<DesignDeployment, Long> {

    /**
     * Deploy a sealed/frozen Version to an Env.
     * <p>
     * This is the main entry point for deployment. It automatically:
     * <ul>
     *   <li>Selects released versions in the sealedTime interval from env.currentVersionId to targetVersionId</li>
     *   <li>Merges version contents and generates DDL</li>
     *   <li>Creates a self-contained Deployment record</li>
     *   <li>Executes the deployment</li>
     *   <li>Updates env.currentVersionId to targetVersionId on success</li>
     *   <li>Auto-freezes the target version after successful PROD deployment</li>
     * </ul>
     *
     * @param envId           Target environment ID
     * @param targetVersionId The version to deploy
     * @return Deployment record ID
     */
    Long deployToEnv(Long envId, Long targetVersionId);

    /**
     * Retry a failed deployment by creating a new Deployment with the same content.
     *
     * @param deploymentId Deployment ID
     * @return New deployment record ID
     */
    Long retryDeployment(Long deploymentId);

    /**
     * Preview the deployment content (merged changes, DDL, preflight checks).
     *
     * @param deploymentId Deployment ID
     * @return Preview DTO with merged content and DDL
     */
    DeploymentPreviewDTO previewDeployment(Long deploymentId);

    /**
     * Preview the DDL SQL of a deployment, combining table DDL and index DDL.
     *
     * @param deploymentId Deployment ID
     * @return DDL SQL string ready for copy to a database client
     */
    String previewDeploymentDDL(Long deploymentId);

}
