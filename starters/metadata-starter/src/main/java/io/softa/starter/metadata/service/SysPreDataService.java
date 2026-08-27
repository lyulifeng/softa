package io.softa.starter.metadata.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.metadata.entity.SysPreData;

/**
 * SysPreData Model Service Interface
 */
public interface SysPreDataService extends EntityService<SysPreData, Long> {

    /**
     * Load the specified list of predefined data files from the root directory: resources/data.
     * Supports data files in JSON, XML, and CSV formats. Data files support a two-layer domain model,
     * i.e., main model and subModel, but they will be created separately when loading.
     * The main model is created first to generate the main model id, then the subModel data is created.
     *
     * @param fileNames List of relative directory data file names to load
     */
    void loadPreSystemData(List<String> fileNames);

    /**
     * Load the specified list of predefined tenant data files from the root directory: resources/data-tenant.
     * Supports data files in JSON, XML, and CSV formats. Data files support a two-layer domain model,
     * i.e., main model and subModel, but they will be created separately when loading.
     * The main model is created first to generate the main model id, then the subModel data is created.
     *
     * @param fileNames List of relative directory tenant data file names to load
     * @param tenantId tenant id to which the data will be loaded
     */
    void loadPreTenantData(List<String> fileNames, Long tenantId);

    /**
     * Load the specified list of predefined platform-tier data files from the root directory:
     * resources/data-platform. Rows land on the platform tier of {@code multiTenant} models —
     * {@code tenantId = BaseConstant.PLATFORM_TENANT_ID} (-1) — owned by the platform operator and
     * invisible to tenant-scoped reads. Same file formats and idempotency (SysPreData ledger keyed by
     * {@code (model, tenantId, preId)}) as the tenant loader.
     *
     * @param fileNames List of relative directory platform data file names to load
     */
    void loadPrePlatformData(List<String> fileNames);

    /**
     * Loads predefined data from a given multipart file.
     * <p>
     * This method processes the provided multipart file to load predefined data
     * into the system. The file is expected to be in a format that is recognized
     * by the implementation, such as CSV, JSON, or XML.
     * </p>
     * <p>
     * The method performs necessary validations and error handling to ensure the
     * data integrity and consistency. Any issues encountered during the file
     * processing are logged appropriately, and relevant exceptions are thrown to
     * inform the caller about the specific problems.
     * </p>
     *
     * @param file the multipart file containing the predefined data to be loaded
     *             into the system. The file should not be null and must contain
     *             valid data as per the required format.
     */
    void loadPreSystemData(MultipartFile file);

}