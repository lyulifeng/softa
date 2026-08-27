package io.softa.starter.metadata.service.impl;

import java.io.Serializable;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.Cast;
import io.softa.framework.orm.domain.FileObject;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.framework.orm.utils.FileUtils;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.starter.metadata.entity.SysPreData;
import io.softa.starter.metadata.service.SysPreDataService;

import static io.softa.framework.orm.constant.ModelConstant.ID;

/**
 * SysPreData Model Service Implementation
 * Predefined data: model + preId as a unique identifier within a loading scope (system, or one
 * tenant), used to bind model row ID. ManyToOne and OneToOne fields directly reference preId,
 * ManyToMany fields reference a list of preIds, OneToMany fields support a data list, where the
 * data in the list does not need to declare the main model's preId but must declare the
 * relatedModel's preId.
 * <p>
 * Scope follows the model, not the file: a binding lives in the scope of the model it binds
 * ({@link #bindingScopeOf}), and a reference resolves in the scope of the model it points AT
 * ({@link #referenceScopeOf}), so references cross the tenancy boundary in either direction. What
 * each file may WRITE is still bounded by its own tenancy ({@link #validateSeedScope}). Cross-scope
 * references make load order load-bearing: system seeds before the tenant seeds referencing them.
 * <p>
 * File-format concerns (JSON / CSV / XML) are delegated to {@link PreDataFormatParser}; this service owns the
 * predefined-data domain logic only — preId binding, main/sub-model ordering, and create-or-update reconciliation.
 */
@Service
public class SysPreDataServiceImpl extends EntityServiceImpl<SysPreData, Long> implements SysPreDataService {

    private final ModelService<Serializable> modelService;
    private final PreDataFormatParser formatParser = new PreDataFormatParser();

    public SysPreDataServiceImpl(ModelService<Serializable> modelService) {
        this.modelService = modelService;
    }

    /**
     * Load the specified list of predefined data files from the root directory: resources/data.
     * Supports data files in JSON, XML, and CSV formats. Data files support a two-layer domain model,
     * i.e., main model and subModel, but they will be created separately when loading.
     * The main model is created first to generate the main model id, then the subModel data is created.
     *
     * @param fileNames List of relative directory data file names to load
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void loadPreSystemData(List<String> fileNames) {
        String dataDir = BaseConstant.PREDEFINED_DATA_SYSTEM_DIR;
        runAsSystemScope(() -> {
            for (String fileName : fileNames) {
                FileObject fileObject = FileUtils.getFileObjectByPath(dataDir, fileName);
                loadFileObject(fileObject);
            }
        });
    }

    /**
     * Load the specified list of predefined tenant data files from the root directory: resources/data-tenant.
     * Supports data files in JSON, XML, and CSV formats. Data files support a two-layer domain model,
     * i.e., main model and subModel, but they will be created separately when loading.
     * The main model is created first to generate the main model id, then the subModel data is created.
     *
     * @param fileNames List of relative directory tenant data file names to load
     * @param tenantId tenant id to which the data will be loaded
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void loadPreTenantData(List<String> fileNames, Long tenantId) {
        if (SystemConfig.env.isEnableMultiTenancy()) {
            Assert.notNull(tenantId,
                    "Loading tenant predefined data requires a tenant id when multi-tenancy is enabled!");
        }
        loadInTenantScope(BaseConstant.PREDEFINED_DATA_TENANT_DIR, fileNames, tenantId);
    }

    /**
     * Platform-tier load: the tenant loader pointed at {@code data-platform/} with the reserved
     * platform tenant id (-1), so seeded rows are platform-owned and invisible to tenant reads.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void loadPrePlatformData(List<String> fileNames) {
        loadInTenantScope(BaseConstant.PREDEFINED_DATA_PLATFORM_DIR, fileNames,
                BaseConstant.PLATFORM_TENANT_ID);
    }

    private void loadInTenantScope(String dataDir, List<String> fileNames, Long tenantId) {
        Context tenantContext = ContextHolder.cloneContext();
        tenantContext.setTenantId(tenantId);
        // Set here as well, even though this path currently works. It works only because the caller is
        // usually an admin *of the tenant being loaded*, so the snapshot resolves. Load another tenant's
        // data — which is exactly what provisioning does — and the caller is not in that tenant's
        // user_role_rel, the snapshot comes back empty, and it fails the same way the system path does.
        // A latent version of the same bug rather than a different one.
        //
        // ContextUtils.inTenantContext expresses the same three settings, but on a `new Context()` — the
        // seeded rows would lose their createdId / createdBy. See runAsSystemScope for the full reason.
        tenantContext.setSkipPermissionCheck(true);
        ContextHolder.runWith(tenantContext, () -> {
            for (String fileName : fileNames) {
                FileObject fileObject = FileUtils.getFileObjectByPath(dataDir, fileName);
                loadFileObject(fileObject);
            }
        });
    }

    /**
     * Loads predefined data from a given multipart file.
     * This method processes the provided multipart file to load predefined data into the system.
     * The file is expected to be in a format recognized by the implementation, such as CSV, JSON, or XML.
     *
     * @param file the multipart file containing the predefined data to be loaded into the system.
     *             The file should not be null and must contain valid data as per the required format.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void loadPreSystemData(MultipartFile file) {
        FileObject fileObject = FileUtils.getFileObject(file);
        runAsSystemScope(() -> loadFileObject(fileObject));
    }

    /**
     * Run a load at system scope: bindings and seeded rows take tenantId = null regardless
     * of the caller's ambient tenant, so a tenant-context caller cannot tenant-stamp system seeds.
     */
    private void runAsSystemScope(Runnable task) {
        Context systemContext = ContextHolder.cloneContext();
        systemContext.setTenantId(null);
        // Row-scope must be skipped too, or a system-scope load cannot read its own bindings.
        //
        // The proof is the asymmetry with loadPreTenantData: the two differ in this one assignment, and
        // tenant loads work while system loads fail. The permission snapshot is keyed by
        // (tenantId, userId), so a real tenant id resolves it, the caller comes back an admin, and
        // appendScopeAccessFilters short-circuits. A null tenant id queries the multi-tenant role tables
        // for a tenant that does not exist, so the snapshot is empty, empty is not an admin,
        // `SysPreData` has a forward scope anchor (`createdId`, so CREATED_BY_SELF applies), and a model
        // with an anchor and no explicit grant is fail-closed to matchNone(). The binding lookup then
        // returns nothing and the load dies on its first referenced row with "the preIDs … do not
        // exist" — naming data that is in fact present, which is why this reads as a data problem.
        //
        // Loading predefined data is an internal operation running under the caller's already-verified
        // authority. That is what skipPermissionCheck is for.
        //
        // Same intent as ContextUtils.inSystemContext, deliberately not that method. Two reasons, both
        // load-bearing: it builds a `new Context()` where this clones, so the caller's userId would be
        // gone and every seeded row plus every binding would carry null createdId / createdBy — the
        // record of who loaded it; and it expresses "system" as crossTenant = true, which waives the
        // tenant predicate on every read in the window, where `tenantId = null` says exactly the one
        // thing needed (write tenant_id = null, read IS NULL). Its own javadoc scopes it to background
        // orchestration rather than request-scoped work, and this is a REST call.
        systemContext.setSkipPermissionCheck(true);
        ContextHolder.runWith(systemContext, task);
    }

    /**
     * Parse a file into its {@code modelName -> data} entries (format handling lives in
     * {@link PreDataFormatParser}) and load each model's predefined data in declaration order.
     *
     * @param fileObject fileObject with the file content
     */
    private void loadFileObject(FileObject fileObject) {
        formatParser.parse(fileObject).forEach(this::processModelData);
    }

    /**
     * Process the model predefined data, which can be a single Map or a List<Map> format.
     *
     * @param model Model name
     * @param predefinedData Predefined data
     */
    private void processModelData(String model, Object predefinedData) {
        ModelManager.validateModel(model);
        if (predefinedData instanceof List<?> listData) {
            listData.forEach(row -> {
                if (row instanceof Map<?, ?> rowMap) {
                    handlePredefinedData(model, Cast.of(rowMap));
                } else {
                    throw new IllegalArgumentException("When defining model data in List structure, " +
                            "the internal data only supports Map format {0}: {1}", model, predefinedData);
                }
            });
        } else if (predefinedData instanceof Map<?, ?> mapData) {
            handlePredefinedData(model, Cast.of(mapData));
        } else {
            throw new IllegalArgumentException(
                    "Model predefined data only supports Map or List<Map> format {0}: {1}", model, predefinedData);
        }
    }

    /**
     * Load a predefined data record.
     * If there is predefined data for OneToMany fields, recursively load the sub-table data after the
     * main row exists (so the generated main id can back-reference into it). The input {@code row} is
     * treated as read-only — it is split into a main-model map and a OneToMany map.
     * When the OneToMany field value is empty, it indicates the deletion of existing associated model data.
     *
     * @param model Model name
     * @param row Predefined data record
     */
    private Serializable handlePredefinedData(String model, Map<String, Object> row) {
        validateSeedScope(model);
        Map<String, Object> mainRow = new LinkedHashMap<>();
        Map<String, Object> oneToManyMap = new LinkedHashMap<>();
        // Separate OneToMany sub-data from the main-model fields; an ordered map keeps the
        // processing order consistent with the file definition.
        row.forEach((field, value) -> {
            if (FieldType.ONE_TO_MANY.equals(ModelManager.getModelField(model, field).getFieldType())) {
                oneToManyMap.put(field, value);
            } else {
                mainRow.put(field, value);
            }
        });
        // Load main model data first, then the OneToMany rows it owns.
        Serializable rowId = createOrUpdateData(model, mainRow);
        loadOneToManyRows(model, rowId, oneToManyMap);
        return rowId;
    }

    /**
     * A seed row must match the scope it is loaded under when multi-tenancy is enabled:
     * a system-scope load writing a multi-tenant model would stamp rows with tenantId = null
     * that no tenant can read, and a tenant-scope load writing a shared model would duplicate
     * the shared rows once per loading tenant. Both directions fail fast; the surrounding
     * transaction rolls the whole file back. Checked here so OneToMany sub-model recursion
     * is covered, not just top-level entries.
     *
     * @param model Model name being seeded
     */
    private void validateSeedScope(String model) {
        if (!SystemConfig.env.isEnableMultiTenancy()) {
            return;
        }
        boolean tenantScope = ContextHolder.getContext().getTenantId() != null;
        boolean tenantModel = ModelManager.getModel(model).isMultiTenant();
        Assert.notTrue(!tenantScope && tenantModel,
                "Model {0} is multi-tenant: load its predefined data per tenant via loadPreTenantData, " +
                "not as system data.", model);
        Assert.notTrue(tenantScope && !tenantModel,
                "Model {0} is a shared model: load its predefined data via loadPreSystemData, " +
                "not as tenant data.", model);
    }

    /**
     * Load OneToMany field data
     * Based on and retain the existing Many side ids, delete Many side data that does not exist in the predefined data file.
     *
     * @param model Main model name
     * @param mainId Main model row ID
     * @param oneToManyMap OneToMany { fieldName: data list} mapping relationship, the value must be a list type.
     */
    private void loadOneToManyRows(String model, Serializable mainId, Map<String, Object> oneToManyMap) {
        oneToManyMap.forEach((field, value) -> {
            Assert.isTrue(value instanceof Collection,
                    "The data of OneToMany field {0}:{1} must be a list: {2}", model, field, value);
            MetaField relation = ModelManager.getModelField(model, field);
            List<Serializable> manyIds = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                Assert.isTrue(item instanceof Map,
                        "The single predefined data of the OneToMany field {0}:{1} must be in Map format: {2}",
                        model, field, item);
                // Copy the child row and inject the back-reference to the main row, leaving the parsed input untouched.
                Map<String, Object> childRow = new LinkedHashMap<>(Cast.<Map<String, Object>>of(item));
                childRow.put(relation.getRelatedField(), mainId);
                manyIds.add(handlePredefinedData(relation.getRelatedModel(), childRow));
            }
            // Delete Many side data but retain those that appear in the predefined data file.
            Filters deleteFilters = new Filters().eq(relation.getRelatedField(), mainId);
            if (!manyIds.isEmpty()) {
                deleteFilters.notIn(ID, manyIds);
            }
            modelService.deleteByFilters(relation.getRelatedModel(), deleteFilters);
        });
    }

    /**
     * Determine whether to create or update predefined data based on whether the main model preId already exists.
     *
     * @param model Model name
     * @param row Predefined data record (main-model fields only)
     * @return Record ID created or updated
     */
    private Serializable createOrUpdateData(String model, Map<String, Object> row) {
        Optional<SysPreData> optionalPreData = getPreDataByPreId(model, row);
        if (optionalPreData.isPresent() && Boolean.TRUE.equals(optionalPreData.get().getFrozen())) {
            // The current data is frozen, and the data ID is returned directly
            return IdUtils.formatId(model, optionalPreData.get().getRowId());
        }
        // Resolve the preIds of ManyToOne, OneToOne, and ManyToMany fields to row IDs (returns a new
        // map; the caller's row is left untouched).
        Map<String, Object> resolved = resolveReferencedPreIds(model, row);
        if (optionalPreData.isEmpty()) {
            // The seed's `id` is the preId (tracking key). For an EXTERNAL_ID model it is ALSO the
            // row's primary key (code-as-id), so it must stay in the row — IdProcessor
            // requires a non-empty id for EXTERNAL_ID. For generated-id strategies it is tracking-only
            // and removed so the strategy assigns the surrogate id.
            String preId = ModelManager.getIdStrategy(model) == IdStrategy.EXTERNAL_ID
                    ? (String) resolved.get(ID)
                    : (String) resolved.remove(ID);
            Serializable rowId = modelService.createOne(model, resolved);
            generatePreData(model, preId, rowId);
            return rowId;
        } else {
            SysPreData preData = optionalPreData.get();
            // Update the data and return the data ID
            Serializable rowId = IdUtils.formatId(model, preData.getRowId());
            resolved.put(ID, rowId);
            // Clear other fields that do not appear in the predefined data
            Set<String> updatableStoredFields = ModelManager.getModelUpdatableFieldsWithoutXToMany(model);
            updatableStoredFields.removeAll(resolved.keySet());
            updatableStoredFields.forEach(fieldName -> resolved.put(fieldName, null));
            boolean result = modelService.updateOne(model, resolved);
            if (!result) {
                boolean isExist = modelService.exist(model, rowId);
                Assert.isTrue(isExist, "Updating predefined data for model {0} ({1}) failed " +
                        "as it has already been physically deleted!", model, preData.getRowId());
            }
            return preData.getRowId();
        }
    }

    /**
     * Get the SysPreData object by preID.
     * Each tenant owns its own binding for a multi-tenant model's preId, so re-loading the same file
     * under another tenant creates that tenant's rows instead of touching the first tenant's. A shared
     * model's binding is written once, at system scope, whoever triggers the load.
     *
     * @param model Model name
     * @param row Predefined data record
     * @return SysPreData object
     */
    private Optional<SysPreData> getPreDataByPreId(String model, Map<String, Object> row) {
        Assert.isTrue(row.containsKey(ID), "Predefined data for model {0} must include the preID: {1}", model, row);
        Object preId = row.get(ID);
        Assert.isTrue(preId instanceof String, "Model {0} predefined data's preId must be of type String: {1}", model, preId);
        return getScopedBindings(model, List.of((String) preId), bindingScopeOf(model)).stream().findFirst();
    }

    /**
     * The scope a model's bindings live in, derived from the model rather than from the load: a
     * multi-tenant model's rows belong to the current tenant, a shared model's are the one globally
     * visible copy and always bind at system scope. Deriving it from the model — rather than reading
     * the ambient tenant — is what makes a cross-scope reference resolvable: a tenant load asking for
     * a shared model's binding must look under {@code tenant_id IS NULL} despite carrying a tenant.
     *
     * <p>Package-private so the scope decision stays unit-testable without driving a whole file load.
     *
     * @param model Model name whose bindings are being addressed
     * @return the scope's tenant id, null for the system scope
     */
    Long bindingScopeOf(String model) {
        return ModelManager.isMultiTenantModel(model) ? ContextHolder.getContext().getTenantId() : null;
    }

    /**
     * Query the bindings of the given preIds within ONE scope: tenantId = T selects a tenant's
     * bindings, null selects the system bindings (tenant_id IS NULL). The single query primitive
     * behind the idempotency lookup and the reference resolution — every binding access is
     * scope-exact, and the scope always comes from {@link #bindingScopeOf} for the model being
     * addressed.
     *
     * @param model Model name
     * @param preIds Predefined IDs
     * @param tenantId tenant id of the scope, null for the system scope
     * @return bindings found in this scope
     */
    private List<SysPreData> getScopedBindings(String model, List<String> preIds, Long tenantId) {
        Filters filters = new Filters().eq(SysPreData::getModel, model).in(SysPreData::getPreId, preIds);
        if (tenantId == null) {
            filters.isNotSet(SysPreData::getTenantId);
        } else {
            filters.eq(SysPreData::getTenantId, tenantId);
        }
        return this.searchList(filters);
    }

    /**
     * Resolve the preIds of ManyToOne, OneToOne, and ManyToMany fields to the bound row IDs, returning a
     * NEW row map — the input {@code row} is left unmodified, so the caller owns the resolved copy.
     *
     * @param model Model name
     * @param row Predefined data record
     * @return a copy of {@code row} with reference preIds replaced by row IDs
     */
    private Map<String, Object> resolveReferencedPreIds(String model, Map<String, Object> row) {
        Map<String, Object> resolved = new LinkedHashMap<>(row);
        for (Map.Entry<String, Object> entry : resolved.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            MetaField metaField = ModelManager.getModelField(model, entry.getKey());
            if (FieldType.TO_ONE_TYPES.contains(metaField.getFieldType())) {
                if (!(entry.getValue() instanceof Long || entry.getValue() instanceof Integer)) {
                    Assert.isTrue(entry.getValue() instanceof String,
                            "Model {0} field {1}:{2} preID must be of type String: {3}",
                            model, entry.getKey(), metaField.getFieldType().getType(), entry.getValue());
                    Serializable rowId = this.getOriginalRowIdByPreId(metaField.getRelatedModel(), Cast.of(entry.getValue()));
                    entry.setValue(rowId);
                }
            } else if (FieldType.MANY_TO_MANY.equals(metaField.getFieldType())) {
                Assert.isTrue(entry.getValue() instanceof Collection,
                        "Model {0} predefined data's {1} ManyToMany field value must be a list or empty",
                        model, entry.getKey());
                if (!CollectionUtils.isEmpty((Collection<?>) entry.getValue())) {
                    List<String> preIds = Cast.of(entry.getValue());
                    List<Serializable> rowIds = this.getOriginalRowIdsByPreIds(metaField.getRelatedModel(), preIds);
                    entry.setValue(rowIds);
                }
            }
        }
        return resolved;
    }

    /**
     * The scope to resolve a reference's preId in: {@link #bindingScopeOf} for the referenced model,
     * plus the one combination that has no answer — a multi-tenant model's bindings exist once per
     * tenant, so resolving one of its preIds from a system-scope load would mean picking a tenant and
     * there is none to pick. Reference such a row by its actual id instead; that path needs no binding
     * and {@link #resolveReferencedPreIds} passes it straight through.
     * <p>
     * Direction across the tenancy boundary is deliberately NOT checked: {@code multiTenant} says
     * whether the ORM narrows reads, not who a row belongs to, so a shared platform-side table
     * legitimately points at a tenant's row — {@code SysPreData} itself is one.
     *
     * <p>Package-private so the rule stays unit-testable without driving a whole file load.
     *
     * @param model Model name being referenced
     * @return the scope's tenant id to resolve in, null for the system scope
     */
    Long referenceScopeOf(String model) {
        Long tenantId = bindingScopeOf(model);
        Assert.notTrue(ModelManager.isMultiTenantModel(model) && tenantId == null,
                "Predefined data references multi-tenant model {0} by preID from a system-scope load: its " +
                "bindings exist once per tenant and this load has no tenant to resolve against. Reference " +
                "the row by its actual id instead, or move this seed to data-tenant.", model);
        return tenantId;
    }

    /**
     * Get the model row ID bound by preId.
     * @param model Model name
     * @param preId Predefined ID
     * @return Model row ID
     */
    private Serializable getOriginalRowIdByPreId(String model, String preId) {
        return getOriginalRowIdsByPreIds(model, List.of(preId)).getFirst();
    }

    /**
     * Get the model row IDs bound by preIds, in the order of the input preIds. Resolution is
     * scope-exact like every binding lookup, in the scope of the model being referenced
     * ({@link #referenceScopeOf}) rather than the scope of the load — a tenant seed resolves a shared
     * model's binding at system scope. Every preId must resolve; the missing ones are reported
     * together.
     *
     * @param model Model name being referenced
     * @param preIds Predefined IDs
     * @return List of model row IDs
     */
    private List<Serializable> getOriginalRowIdsByPreIds(String model, List<String> preIds) {
        Long tenantId = referenceScopeOf(model);
        Map<String, Serializable> resolved = new HashMap<>();
        getScopedBindings(model, preIds, tenantId).forEach(binding ->
                resolved.putIfAbsent(binding.getPreId(), IdUtils.formatId(model, binding.getRowId())));
        List<String> missing = preIds.stream().filter(preId -> !resolved.containsKey(preId)).toList();
        Assert.isTrue(missing.isEmpty(), "The preIDs of the predefined data for model {0}: {1} do not exist " +
                "in the predefined data table and may not have been created yet! Referenced data must be " +
                "loaded first — for a shared model that means loading the system seeds before this one.",
                model, missing);
        return preIds.stream().map(resolved::get).toList();
    }

    /**
     * Create predefined data and bind the model row ID.
     *
     * @param model Model name
     * @param preId Predefined ID
     * @param rowId Model record ID
     */
    private void generatePreData(String model, String preId, Serializable rowId) {
        SysPreData preData = new SysPreData();
        preData.setModel(model);
        preData.setPreId(preId);
        preData.setRowId(rowId.toString());
        // Stamp the scope of the model being bound — exactly what fillTenantFieldForInsert put on the
        // seeded row itself, so the binding and the row it points at cannot land in different scopes.
        preData.setTenantId(bindingScopeOf(model));
        this.createOne(preData);
    }
}
