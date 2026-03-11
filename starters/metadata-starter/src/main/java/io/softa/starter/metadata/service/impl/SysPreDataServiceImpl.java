package io.softa.starter.metadata.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.exception.SystemException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.Cast;
import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.base.utils.LambdaUtils;
import io.softa.framework.orm.domain.FileObject;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.FileType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.framework.orm.utils.FileUtils;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.starter.metadata.entity.SysPreData;
import io.softa.starter.metadata.entity.TenantOptionItem;
import io.softa.starter.metadata.entity.TenantOptionSet;
import io.softa.starter.metadata.service.TenantOptionItemService;
import io.softa.starter.metadata.service.TenantOptionSetService;
import io.softa.starter.metadata.service.SysPreDataService;

import static io.softa.framework.orm.constant.ModelConstant.ID;

/**
 * SysPreData Model Service Implementation
 * Predefined data: model + preId as a unique identifier, used to bind model row ID, thus preId is unique within the model.
 * Among them, ManyToOne and OneToOne fields directly reference preId, ManyToMany fields reference a list of preIds,
 * OneToMany fields support a data list, where the data in the list does not need to declare the main model's preId
 * but must declare the relatedModel's preId.
 */
@Service
public class SysPreDataServiceImpl extends EntityServiceImpl<SysPreData, Long> implements SysPreDataService {

    @Autowired
    protected ModelService<Serializable> modelService;

    @Autowired
    private TenantOptionSetService tenantOptionSetService;

    @Autowired
    private TenantOptionItemService tenantOptionItemService;

    private static final String OPTION_SET_NAME = "option_set_name";
    private static final String OPTION_SET_CODE = "option_set_code";
    private static final String OPTION_SET_ITEM_NAME = "option_set_item_name";
    private static final String OPTION_SET_ITEM_CODE = "option_set_item_code";
    private static final String PARENT_OPTION_SET_CODE = "parent_option_set_code";
    private static final String PARENT_OPTION_SET_ITEM_CODE = "parent_option_set_item_code";
    private static final String PARENT_ITEM_ID = "parentItemId";
    private static final String TENANT_OPTION_SET_FILE_NAME = "option_set";

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
        for (String fileName : fileNames) {
            FileObject fileObject = FileUtils.getFileObjectByPath(dataDir, fileName);
            loadFileObject(fileObject);
        }
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
        String dataDir = BaseConstant.PREDEFINED_DATA_TENANT_DIR;
        Context tenantContext = ContextHolder.cloneContext();
        tenantContext.setTenantId(tenantId);
        ContextHolder.runWith(tenantContext, () -> {
            for (String fileName : fileNames) {
                FileType fileType = getFileTypeByName(fileName);
                if (FileType.XLS.equals(fileType) || FileType.XLSX.equals(fileType)) {
                    if (!isTenantOptionSetExcel(fileName)) {
                        continue;
                    }
                    loadTenantOptionSetExcel(dataDir, fileName);
                    continue;
                }
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
        loadFileObject(fileObject);
    }

    /**
     * Load fileObject and process different data formats based on the file type.
     *
     * @param fileObject fileObject with the file content
     */
    private void loadFileObject(FileObject fileObject) {
        if (StringUtils.isBlank(fileObject.getContent())) {
            return;
        }
        if (FileType.JSON.equals(fileObject.getFileType())) {
            processJson(fileObject.getContent());
        } else if (FileType.XML.equals(fileObject.getFileType())) {
            processXml(fileObject.getContent());
        } else if (FileType.CSV.equals(fileObject.getFileType())) {
            // Treats the first part of the file name as the model name
            String fileName = fileObject.getFileName();
            String modelName = fileName.substring(0, fileName.indexOf('.')).trim();
            Assert.isTrue(ModelManager.existModel(modelName),
                    "Model {0} specified in the fileName `{1}` does not exist!", modelName, fileName);
            processCsv(modelName, fileObject.getContent());
        } else {
            throw new IllegalArgumentException("Unsupported file type for predefined data: {0}", fileObject.getFileType());
        }
    }

    private FileType getFileTypeByName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        Assert.isTrue(dotIndex > -1, "The file {0} has no extension!", fileName);
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return FileType.ofExtension(extension)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type for predefined data: {0}", extension));
    }

    private boolean isTenantOptionSetExcel(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        String shortFileName = dotIndex > -1 ? fileName.substring(0, dotIndex) : fileName;
        int slashIndex = Math.max(shortFileName.lastIndexOf('/'), shortFileName.lastIndexOf('\\'));
        String pureFileName = slashIndex > -1 ? shortFileName.substring(slashIndex + 1) : shortFileName;
        return TENANT_OPTION_SET_FILE_NAME.equalsIgnoreCase(pureFileName);
    }

    private void loadTenantOptionSetExcel(String dataDir, String fileName) {
        String fullName = dataDir + fileName;
        ClassPathResource resource = new ClassPathResource(fullName);
        Assert.isTrue(resource.exists(), "File does not exist: {0}", fullName);
        try (InputStream inputStream = resource.getInputStream()) {
            importTenantOptionSetRows(readTenantOptionSetExcel(inputStream));
        } catch (IOException e) {
            throw new SystemException("Failed to read Excel file: {0}", fileName, e);
        }
    }

    private List<TenantOptionExcelRow> readTenantOptionSetExcel(InputStream inputStream) {
        List<TenantOptionExcelRow> rows = new ArrayList<>();
        FesodSheet.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {
            private Map<Integer, String> headerMap = new HashMap<>();

            @Override
            public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
                TenantOptionExcelRow row = new TenantOptionExcelRow();
                rowData.forEach((columnIndex, value) -> {
                    String header = headerMap.get(columnIndex);
                    if (header == null) {
                        return;
                    }
                    String normalizedValue = StringUtils.trimToEmpty(value);
                    switch (header.trim()) {
                        case OPTION_SET_NAME -> row.setOptionSetName(normalizedValue);
                        case OPTION_SET_CODE -> row.setOptionSetCode(normalizedValue);
                        case OPTION_SET_ITEM_NAME -> row.setOptionSetItemName(normalizedValue);
                        case OPTION_SET_ITEM_CODE -> row.setOptionSetItemCode(normalizedValue);
                        case PARENT_OPTION_SET_CODE -> row.setParentOptionSetCode(normalizedValue);
                        case PARENT_OPTION_SET_ITEM_CODE -> row.setParentOptionSetItemCode(normalizedValue);
                        default -> {
                        }
                    }
                });
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }

            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                this.headerMap = headMap;
                validateTenantOptionExcelHeader(headMap.values());
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet(0).doRead();
        Assert.notEmpty(rows, "No data exists in the excel file!");
        return rows;
    }

    private void validateTenantOptionExcelHeader(Collection<String> headers) {
        Set<String> actualHeaders = headers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());
        List<String> requiredHeaders = List.of(
                OPTION_SET_NAME,
                OPTION_SET_CODE,
                OPTION_SET_ITEM_NAME,
                OPTION_SET_ITEM_CODE,
                PARENT_OPTION_SET_CODE,
                PARENT_OPTION_SET_ITEM_CODE
        );
        requiredHeaders.forEach(header ->
                Assert.isTrue(actualHeaders.contains(header), "The Excel header `{0}` is required!", header));
    }

    private void importTenantOptionSetRows(List<TenantOptionExcelRow> rows) {
        Map<String, Long> optionSetIdMap = new LinkedHashMap<>();
        for (TenantOptionExcelRow row : rows) {
            row.validate();
            Long optionSetId = upsertOptionSet(row);
            optionSetIdMap.put(row.getOptionSetCode(), optionSetId);
        }
        Map<String, Long> itemIdMap = new HashMap<>();
        for (TenantOptionExcelRow row : rows) {
            Long itemId = upsertOptionItem(row, optionSetIdMap.get(row.getOptionSetCode()));
            itemIdMap.put(buildItemKey(row.getOptionSetCode(), row.getOptionSetItemCode()), itemId);
        }
        for (TenantOptionExcelRow row : rows) {
            updateParentItemId(row, itemIdMap);
        }
    }

    private Long upsertOptionSet(TenantOptionExcelRow row) {
        Filters filters = new Filters().eq(TenantOptionSet::getOptionSetCode, row.getOptionSetCode());
        Optional<TenantOptionSet> existing = tenantOptionSetService.searchOne(new FlexQuery(filters));
        if (existing.isPresent()) {
            TenantOptionSet optionSet = existing.get();
            optionSet.setName(row.getOptionSetName());
            tenantOptionSetService.updateOne(optionSet);
            return optionSet.getId();
        }
        TenantOptionSet optionSet = new TenantOptionSet();
        optionSet.setName(row.getOptionSetName());
        optionSet.setOptionSetCode(row.getOptionSetCode());
        return tenantOptionSetService.createOne(optionSet);
    }

    private Long upsertOptionItem(TenantOptionExcelRow row, Long optionSetId) {
        Filters filters = new Filters()
                .eq(TenantOptionItem::getOptionSetCode, row.getOptionSetCode())
                .eq(TenantOptionItem::getItemCode, row.getOptionSetItemCode());
        Optional<TenantOptionItem> existing = tenantOptionItemService.searchOne(new FlexQuery(filters));
        Map<String, Object> itemRow = new HashMap<>();
        itemRow.put("optionSetId", optionSetId);
        itemRow.put("optionSetCode", row.getOptionSetCode());
        itemRow.put("itemCode", row.getOptionSetItemCode());
        itemRow.put("itemName", row.getOptionSetItemName());
        if (existing.isPresent()) {
            itemRow.put(ID, existing.get().getId());
            modelService.updateOne(TenantOptionItem.class.getSimpleName(), itemRow);
            return existing.get().getId();
        }
        return Cast.of(modelService.createOne(TenantOptionItem.class.getSimpleName(), itemRow));
    }

    private void updateParentItemId(TenantOptionExcelRow row, Map<String, Long> itemIdMap) {
        Long itemId = itemIdMap.get(buildItemKey(row.getOptionSetCode(), row.getOptionSetItemCode()));
        Assert.notNull(itemId, "Option item `{0}` in option set `{1}` was not created successfully!",
                row.getOptionSetItemCode(), row.getOptionSetCode());
        Long parentItemId = null;
        if (StringUtils.isNotBlank(row.getParentOptionSetItemCode())) {
            String parentOptionSetCode = row.getResolvedParentOptionSetCode();
            parentItemId = itemIdMap.get(buildItemKey(parentOptionSetCode, row.getParentOptionSetItemCode()));
            if (parentItemId == null) {
                Filters filters = new Filters()
                        .eq(TenantOptionItem::getOptionSetCode, parentOptionSetCode)
                        .eq(TenantOptionItem::getItemCode, row.getParentOptionSetItemCode());
                parentItemId = tenantOptionItemService.searchOne(new FlexQuery(filters))
                        .map(TenantOptionItem::getId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Parent option item code `{0}` does not exist in option set `{1}`!",
                                row.getParentOptionSetItemCode(), parentOptionSetCode));
            }
        }
        Map<String, Object> updateRow = new HashMap<>();
        updateRow.put(ID, itemId);
        updateRow.put(PARENT_ITEM_ID, parentItemId);
        modelService.updateOne(TenantOptionItem.class.getSimpleName(), updateRow);
    }

    private String buildItemKey(String optionSetCode, String itemCode) {
        return optionSetCode + "::" + itemCode;
    }

    /**
     * Process JSON format data, parse and map, according to the data order in the JSON text to LinkedHashMap,
     * and process in order. JSON format data supports two-layer model nesting, i.e., main model and relatedModel,
     * but they will be created separately when loading. The main model data is created first to generate the ID,
     * then the relatedModel data is created.
     * <p>
     *     Data under the JSON model supports two formats:
     *     <ul>
     *         <li>Single Map format: { model1: {field1: value1, field2: value2, ...}, model2: {...}, ...}</li>
     *         <li>List<Map> format: { model1: [{field1: value1, field2: value2}, {...}], model2: {...}, ...}</li>
     * </p>
     *
     * @param content JSON string data content
     */
    private void processJson(String content) {
        Map<String, Object> predefinedData = JsonUtils.stringToObject(content, new TypeReference<LinkedHashMap<String, Object>>() {});
        predefinedData.forEach(this::processModelData);
    }

    private void processXml(String content) {
        // TODO: Process the data of XML format
    }

    /**
     * Process CSV format data, parse and map, according to the data order in the CSV text.
     * The first line of the CSV content is the header, and the data content is converted to a list of maps.
     * The default separator is comma, and the quote character is double quote
     *
     * @param modelName Model name
     * @param content CSV string data content
     */
    private void processCsv(String modelName, String content) {
        // Parse the CSV content using CSVParser, automatically detect and skip the first line as the header
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();
        // Parse the CSV content using CSVParser
        CSVParser parser;
        try {
            parser = csvFormat.parse(new StringReader(content));
        } catch (IOException e) {
            throw new SystemException("Failed to parse the CSV content: {0}", e.getMessage());
        }
        // Get the header map of the CSV content
        Map<String, Integer> headerMap = parser.getHeaderMap();
        List<Map<String, Object>> csvDataList = new ArrayList<>();
        // Iterate over each record in the CSV content, not including the header
        for (CSVRecord record : parser) {
            Map<String, Object> rowData = new HashMap<>();
            for (Map.Entry<String, Integer> header : headerMap.entrySet()) {
                String fieldName = header.getKey().trim();
                Assert.notBlank(fieldName, "The field name in the CSV header cannot be empty!");
                String stringValue = record.get(header.getValue()).trim();
                FieldType fieldType = ModelManager.getModelField(modelName, fieldName).getFieldType();
                if (ModelConstant.ID.equals(fieldName) || FieldType.TO_ONE_TYPES.contains(fieldType)) {
                    // Retain the preID of ID, ManyToOne, and OneToOne fields, which are String value.
                    rowData.put(fieldName, stringValue);
                } else {
                    Object fieldValue = FieldType.convertStringToFieldValue(fieldType, stringValue);
                    rowData.put(fieldName, fieldValue);
                }
            }
            csvDataList.add(rowData);
        }
        // Process the model data list
        processModelData(modelName, csvDataList);
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
     * If there is predefined data for OneToMany and ManyToMany fields, recursively load the sub-table data,
     * using an ordered Map to ensure that the data processing order is consistent with the file definition.
     * When the OneToMany field value is empty, it indicates the deletion of existing associated model data.
     *
     * @param model Model name
     * @param row Predefined data record
     */
    private Serializable handlePredefinedData(String model, Map<String, Object> row) {
        Map<String, Object> oneToManyMap = new LinkedHashMap<>();
        // Extract OneToMany fields contained in the predefined data and put them in the OneToManyMap
        // and then remove them from the row, used to independently handle the creation or update of the associated model.
        Set<String> oneToManyFields = row.keySet().stream()
                .filter(field -> FieldType.ONE_TO_MANY.equals(ModelManager.getModelField(model, field).getFieldType()))
                .collect(Collectors.toSet());
        oneToManyFields.forEach(field -> oneToManyMap.put(field, row.remove(field)));
        // Load main model data
        Serializable rowId = createOrUpdateData(model, row);
        // Load OneToMany data
        loadOneToManyRows(model, rowId, oneToManyMap);
        return rowId;
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
        oneToManyMap.forEach((field, rows) -> {
            if (!(rows instanceof Collection)) {
                throw new IllegalArgumentException("The data of OneToMany field {0}:{1} must be a list: {2}", model, field, rows);
            }
            MetaField oneToManyMetaField = ModelManager.getModelField(model, field);
            // Process each item in rows, ensuring each is a Map
            List<Serializable> manyIds = ((Collection<?>) rows).stream()
                    .peek(item -> {
                        if (!(item instanceof Map)) {
                            throw new IllegalArgumentException(
                                    "The single predefined data of the OneToMany field {0}:{1} must be in Map format: {2}",
                                    model, field, item);
                        }
                    })
                    .map(item -> {
                        Map<String, Object> castedItem = Cast.of(item);
                        castedItem.put(oneToManyMetaField.getRelatedField(), mainId);
                        // Load OneToMany single row data
                        return handlePredefinedData(oneToManyMetaField.getRelatedModel(), castedItem);
                    })
                    .toList();
            // Delete Many side data but retain those that appear in the predefined data file.
            Filters deleteFilters = new Filters().eq(oneToManyMetaField.getRelatedField(), mainId);
            if (!manyIds.isEmpty()) {
                deleteFilters.notIn(ID, manyIds);
            }
            modelService.deleteByFilters(oneToManyMetaField.getRelatedModel(), deleteFilters);
        });
    }

    /**
     * Determine whether to create or update predefined data based on whether the main model preId already exists.
     *
     * @param model Model name
     * @param row Predefined data record
     * @return Record ID created or updated
     */
    private Serializable createOrUpdateData(String model, Map<String, Object> row) {
        Optional<SysPreData> optionalPreData = getPreDataByPreId(model, row);
        if (optionalPreData.isPresent() && Boolean.TRUE.equals(optionalPreData.get().getFrozen())) {
            // The current data is frozen, and the data ID is returned directly
            return IdUtils.formatId(model, optionalPreData.get().getRowId());
        }
        // Replace the preID of ManyToOne, OneToOne, and ManyToMany fields with the row ID
        this.replaceReferencedPreIds(model, row);
        if (optionalPreData.isEmpty()) {
            // Create the data and return the data ID
            String preId = (String) row.get(ID);
            row.remove(ID);
            Serializable rowId = modelService.createOne(model, row);
            generatePreData(model, preId, rowId);
            return rowId;
        } else {
            SysPreData preData = optionalPreData.get();
            // Update the data and return the data ID
            Serializable rowId = IdUtils.formatId(model, preData.getRowId());
            row.put(ID, rowId);
            // Clear other fields that do not appear in the predefined data
            Set<String> updatableStoredFields = ModelManager.getModelUpdatableFieldsWithoutXToMany(model);
            updatableStoredFields.removeAll(row.keySet());
            updatableStoredFields.forEach(field -> row.put(field, null));
            boolean result = modelService.updateOne(model, row);
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
     * @param model Model name
     * @param row Predefined data record
     * @return SysPreData object
     */
    private Optional<SysPreData> getPreDataByPreId(String model, Map<String, Object> row) {
        Assert.isTrue(row.containsKey(ID), "Predefined data for model {0} must include the preID: {1}", model, row);
        Object preId = row.get(ID);
        Assert.isTrue(preId instanceof String, "Model {0} predefined data's preId must be of type String: {1}", model, preId);
        Filters filters = new Filters().eq(SysPreData::getModel, model).eq(SysPreData::getPreId, preId);
        return this.searchOne(new FlexQuery(filters));
    }

    /**
     * Replace the pre-defined ID of ManyToOne, OneToOne, and ManyToMany fields with the row ID.
     *
     * @param model Model name
     * @param row Predefined data record
     */
    private void replaceReferencedPreIds(String model, Map<String, Object> row) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            MetaField metaField = ModelManager.getModelField(model, entry.getKey());
            if (FieldType.TO_ONE_TYPES.contains(metaField.getFieldType()) &&
                    !(entry.getValue() instanceof Long || entry.getValue() instanceof Integer)) {
                Assert.isTrue(entry.getValue() instanceof String,
                        "Model {0} field {1}:{2} preID must be of type String: {3}",
                        model, entry.getKey(), metaField.getFieldType().getType(), entry.getValue());
                Serializable rowId = this.getOriginalRowIdByPreId(metaField.getRelatedModel(), Cast.of(entry.getValue()));
                entry.setValue(rowId);
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
    }

    /**
     * Get the model row ID bound by preId.
     * @param model Model name
     * @param preId Predefined ID
     * @return Model row ID
     */
    private Serializable getOriginalRowIdByPreId(String model, String preId) {
        Filters filters = new Filters().eq(SysPreData::getModel, model).eq(SysPreData::getPreId, preId);
        String rowIdField = LambdaUtils.getAttributeName(SysPreData::getRowId);
        List<Serializable> rowIds = this.getRelatedIds(filters, rowIdField);
        Assert.notEmpty(rowIds, "The preID of the predefined data for model {0}: {1} does not exist " +
                "in the predefined data table and may not have been created yet!", model, preId);
        return IdUtils.formatId(model, rowIds.getFirst());
    }

    /**
     * Get a list of model row IDs bound by preIds.
     * @param model Model name
     * @param preIds Predefined IDs
     * @return List of model row IDs
     */
    private List<Serializable> getOriginalRowIdsByPreIds(String model, List<String> preIds) {
        Filters filters = new Filters().eq(SysPreData::getModel, model).in(SysPreData::getPreId, preIds);
        String rowIdField = LambdaUtils.getAttributeName(SysPreData::getRowId);
        List<Serializable> rowIds = this.getRelatedIds(filters, rowIdField);
        Assert.notEmpty(rowIds, "The preIDs of the predefined data for model {0}: {1} do not exist " +
                "in the predefined data table and may not have been created yet!", model, preIds);
        return IdUtils.formatIds(model, rowIds);
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
        this.createOne(preData);
    }

    private static class TenantOptionExcelRow {
        private String optionSetName;
        private String optionSetCode;
        private String optionSetItemName;
        private String optionSetItemCode;
        private String parentOptionSetCode;
        private String parentOptionSetItemCode;

        boolean isEmpty() {
            return StringUtils.isAllBlank(optionSetName, optionSetCode, optionSetItemName, optionSetItemCode,
                    parentOptionSetCode, parentOptionSetItemCode);
        }

        void validate() {
            Assert.notBlank(optionSetName, "The Excel field `{0}` cannot be empty!", OPTION_SET_NAME);
            Assert.notBlank(optionSetCode, "The Excel field `{0}` cannot be empty!", OPTION_SET_CODE);
            Assert.notBlank(optionSetItemName, "The Excel field `{0}` cannot be empty!", OPTION_SET_ITEM_NAME);
            Assert.notBlank(optionSetItemCode, "The Excel field `{0}` cannot be empty!", OPTION_SET_ITEM_CODE);
            boolean hasParentOptionSetCode = StringUtils.isNotBlank(parentOptionSetCode);
            boolean hasParentOptionSetItemCode = StringUtils.isNotBlank(parentOptionSetItemCode);
            Assert.isTrue(hasParentOptionSetCode == hasParentOptionSetItemCode,
                    "The Excel fields `{0}` and `{1}` must both be filled or both be empty!",
                    PARENT_OPTION_SET_CODE, PARENT_OPTION_SET_ITEM_CODE);
        }

        String getResolvedParentOptionSetCode() {
            return StringUtils.defaultIfBlank(parentOptionSetCode, optionSetCode);
        }


        public String getOptionSetName() {
            return optionSetName;
        }

        public void setOptionSetName(String optionSetName) {
            this.optionSetName = optionSetName;
        }

        public String getOptionSetCode() {
            return optionSetCode;
        }

        public void setOptionSetCode(String optionSetCode) {
            this.optionSetCode = optionSetCode;
        }

        public String getOptionSetItemName() {
            return optionSetItemName;
        }

        public void setOptionSetItemName(String optionSetItemName) {
            this.optionSetItemName = optionSetItemName;
        }

        public String getOptionSetItemCode() {
            return optionSetItemCode;
        }

        public void setOptionSetItemCode(String optionSetItemCode) {
            this.optionSetItemCode = optionSetItemCode;
        }

        public String getParentOptionSetItemCode() {
            return parentOptionSetItemCode;
        }

        public void setParentOptionSetItemCode(String parentOptionSetItemCode) {
            this.parentOptionSetItemCode = parentOptionSetItemCode;
        }

        public String getParentOptionSetCode() {
            return parentOptionSetCode;
        }

        public void setParentOptionSetCode(String parentOptionSetCode) {
            this.parentOptionSetCode = parentOptionSetCode;
        }
    }
}
