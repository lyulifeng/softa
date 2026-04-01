package io.softa.starter.file.excel.imports;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.exception.ValidationException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.constant.FileConstant;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportFieldDTO;

/**
 * Resolves dotted-path relation lookup fields (e.g. deptId.code)
 * by looking up the related model's business key and writing back the real FK id.
 *
 * <p>Design semantics:
 * <ul>
 *   <li>{@code deptId} — direct FK id import (no lookup needed)</li>
 *   <li>{@code deptId.code} — use Department.code to reverse-lookup, write back deptId</li>
 * </ul>
 *
 * <p>Rules:
 * <ul>
 *   <li>A fieldName containing a dot whose root field is ManyToOne/OneToOne is treated as a relation lookup field.</li>
 *   <li>Only one level of cascade is supported: {@code deptId.code} is OK, {@code deptId.companyId.code} is NOT.</li>
 *   <li>A direct FK field (e.g. {@code deptId}) and a lookup field (e.g. {@code deptId.code}) must not coexist in the same template.</li>
 * </ul>
 */
@Slf4j
@Component
public class RelationLookupResolver {

    @Autowired
    private ModelService<?> modelService;

    /**
     * Describes a group of dotted-path lookup fields sharing the same root FK field.
     * For example, deptId.code and deptId.name would form one group with rootField="deptId".
     *
     * @param rootField the relation field in the main model, for example {@code deptId} or {@code roleIds}
     * @param relatedModel the related model name used for reverse lookup
     * @param lookupFields the business-key fields in the related model, for example {@code ["code"]}
     * @param dottedPaths the original template field names, for example {@code ["deptId.code"]}
     * @param ignoreEmpty whether empty source values should leave the root field untouched
     * @param toMany whether the root relation field is a to-many relation
     */
    public record LookupGroup(String rootField, String relatedModel, List<String> lookupFields,
                              List<String> dottedPaths, boolean ignoreEmpty, boolean toMany) {}

    /**
     * Detect, validate and return the lookup groups from the import field list.
     *
     * @param modelName the target model name
     * @param importFields the list of import field DTOs
     * @return a list of LookupGroup describing the relation lookups
     */
    public List<LookupGroup> detectLookupGroups(String modelName, List<ImportFieldDTO> importFields) {
        // Collect all fieldNames
        Set<String> directFields = new HashSet<>();
        // rootField -> list of ImportFieldDTOs with dotted paths
        Map<String, List<ImportFieldDTO>> rootToDottedFields = new LinkedHashMap<>();

        for (ImportFieldDTO field : importFields) {
            String fieldName = field.getFieldName();
            if (!fieldName.contains(".")) {
                directFields.add(fieldName);
                continue;
            }
            // Has dot: validate relation lookup
            String[] parts = fieldName.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Import field `{0}` has more than one level of cascade. " +
                        "Only single-level relation lookup is supported (e.g. deptId.code). " +
                        "For deeper cascades, consider using a cascaded field.",
                        fieldName);
            }

            String rootField = parts[0];
            // Validate root field is ManyToOne/OneToOne
            if (!ModelManager.existField(modelName, rootField)) {
                throw new IllegalArgumentException(
                        "Import field `{0}`: root field `{1}` does not exist in model `{2}`.",
                        fieldName, rootField, modelName);
            }
            MetaField rootMetaField = ModelManager.getModelField(modelName, rootField);
            if (!FieldType.RELATED_TYPES.contains(rootMetaField.getFieldType())) {
                throw new IllegalArgumentException(
                        "Import field `{0}`: root field `{1}` must be a relation field, but is `{2}`.",
                        fieldName, rootField, rootMetaField.getFieldType());
            }
            rootToDottedFields.computeIfAbsent(rootField, ignored -> new ArrayList<>()).add(field);
        }

        for (String rootField : rootToDottedFields.keySet()) {
            if (directFields.contains(rootField)) {
                List<String> paths = rootToDottedFields.get(rootField).stream()
                        .map(ImportFieldDTO::getFieldName).toList();
                throw new IllegalArgumentException(
                        "Import field `{0}` and `{1}` cannot coexist. " +
                                "Either import the relation value directly or use relation lookup, not both.",
                        rootField, paths);
            }
        }

        // Build lookup groups
        List<LookupGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ImportFieldDTO>> entry : rootToDottedFields.entrySet()) {
            String rootField = entry.getKey();
            List<ImportFieldDTO> fieldDTOs = entry.getValue();
            MetaField rootMetaField = ModelManager.getModelField(modelName, rootField);
            String relatedModel = rootMetaField.getRelatedModel();
            List<String> dottedPaths = fieldDTOs.stream().map(ImportFieldDTO::getFieldName).toList();
            Assert.notBlank(relatedModel,
                    "Import field `{0}`: root field `{1}` has no related model configured.",
                    dottedPaths, rootField);
            // Extract the lookup field names in the related model (e.g. "code" from "deptId.code")
            List<String> lookupFields = dottedPaths.stream()
                    .map(path -> path.substring(rootField.length() + 1))
                    .toList();
            boolean ignoreEmpty = Boolean.TRUE.equals(fieldDTOs.getFirst().getIgnoreEmpty());
            boolean toMany = FieldType.TO_MANY_TYPES.contains(rootMetaField.getFieldType());
            groups.add(new LookupGroup(rootField, relatedModel, lookupFields, dottedPaths, ignoreEmpty, toMany));
        }
        return groups;
    }

    /**
     * Resolve all relation lookup fields in the rows: for each lookup group,
     * batch-query the related model, write back the real FK id, and remove the dotted-path temporary columns.
     *
     * @param rows the import data rows
     * @param lookupGroups the lookup groups detected by {@link #detectLookupGroups}
     * @param skipException when false, throw ValidationException on lookup failure instead of marking FAILED_REASON
     */
    public void resolveRows(List<Map<String, Object>> rows, List<LookupGroup> lookupGroups, boolean skipException) {
        for (LookupGroup group : lookupGroups) {
            if (group.toMany()) {
                resolveToManyGroup(rows, group, skipException);
            } else {
                resolveToOneGroup(rows, group, skipException);
            }
        }
    }

    /**
     * Resolve one lookup group across all rows.
     */
    private void resolveToOneGroup(List<Map<String, Object>> rows, LookupGroup group, boolean skipException) {
        Set<List<Object>> distinctKeys = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if (row.containsKey(FileConstant.FAILED_REASON)) {
                continue;
            }
            List<Object> keyValues = extractKeyValues(row, group);
            if (keyValues != null) {
                distinctKeys.add(keyValues);
            }
        }

        if (distinctKeys.isEmpty()) {
            // All values are empty/null — handle empty rootField and clean up dotted paths
            handleEmptyAndCleanup(rows, group);
            return;
        }

        // Step 2: Batch query related model to get businessKey -> id mapping
        Map<List<Object>, ?> keyToIdMap = modelService.getIdsByBusinessKeys(
                group.relatedModel(), group.lookupFields(), distinctKeys);

        // Step 3: Write back the FK id and remove dotted-path columns
        for (Map<String, Object> row : rows) {
            if (row.containsKey(FileConstant.FAILED_REASON)) {
                removeDottedPaths(row, group);
                continue;
            }
            List<Object> keyValues = extractKeyValues(row, group);
            if (keyValues == null) {
                handleEmptyRootField(row, group);
                removeDottedPaths(row, group);
                continue;
            }
            Object resolvedId = keyToIdMap.get(keyValues);
            if (resolvedId == null) {
                markFailure(row, buildNotFoundMessage(group, keyValues), skipException);
            } else {
                row.put(group.rootField(), resolvedId);
            }
            removeDottedPaths(row, group);
        }
    }

    /**
     * Resolves one to-many lookup group.
     *
     * <p>Example: {@code roleIds.code -> roleIds} where the source cell may contain
     * comma-separated values such as {@code ADMIN,USER}.</p>
     */
    private void resolveToManyGroup(List<Map<String, Object>> rows, LookupGroup group, boolean skipException) {
        Set<List<Object>> distinctKeys = new LinkedHashSet<>();
        Map<Map<String, Object>, List<List<Object>>> rowResolvedKeys = new IdentityHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row.containsKey(FileConstant.FAILED_REASON)) {
                continue;
            }
            List<List<Object>> keyGroups = extractToManyKeyValues(row, group);
            if (keyGroups != null) {
                rowResolvedKeys.put(row, keyGroups);
                distinctKeys.addAll(keyGroups);
            }
        }

        if (distinctKeys.isEmpty()) {
            handleEmptyAndCleanup(rows, group);
            return;
        }

        Map<List<Object>, ?> keyToIdMap = modelService.getIdsByBusinessKeys(
                group.relatedModel(), group.lookupFields(), distinctKeys);

        for (Map<String, Object> row : rows) {
            if (row.containsKey(FileConstant.FAILED_REASON)) {
                removeDottedPaths(row, group);
                continue;
            }
            List<List<Object>> keyGroups = rowResolvedKeys.get(row);
            if (keyGroups == null) {
                handleEmptyRootField(row, group);
                removeDottedPaths(row, group);
                continue;
            }
            List<Object> resolvedIds = new ArrayList<>();
            List<List<Object>> missingKeys = new ArrayList<>();
            for (List<Object> keyValues : keyGroups) {
                Object resolvedId = keyToIdMap.get(keyValues);
                if (resolvedId == null) {
                    missingKeys.add(keyValues);
                } else {
                    resolvedIds.add(resolvedId);
                }
            }
            if (!missingKeys.isEmpty()) {
                String message = missingKeys.stream().map(keys -> buildNotFoundMessage(group, keys))
                        .collect(Collectors.joining("; "));
                markFailure(row, message, skipException);
            } else {
                row.put(group.rootField(), resolvedIds);
            }
            removeDottedPaths(row, group);
        }
    }

    /**
     * Handles a lookup failure according to the import mode.
     *
     * <ul>
     *   <li>fail-fast mode: throws {@link ValidationException}</li>
     *   <li>skip mode: appends the message to {@link FileConstant#FAILED_REASON}</li>
     * </ul>
     */
    private void markFailure(Map<String, Object> row, String message, boolean skipException) {
        if (!skipException) {
            throw new ValidationException(message);
        }
        String failedReason = row.containsKey(FileConstant.FAILED_REASON)
                ? row.get(FileConstant.FAILED_REASON) + "; " : "";
        row.put(FileConstant.FAILED_REASON, failedReason + message);
    }

    /**
     * Applies empty-value semantics for the resolved root relation field.
     *
     * <ul>
     *   <li>{@code ignoreEmpty=true}: do not write the root field</li>
     *   <li>to-one + {@code ignoreEmpty=false}: write {@code null}</li>
     *   <li>to-many + {@code ignoreEmpty=false}: write an empty list</li>
     * </ul>
     */
    private void handleEmptyRootField(Map<String, Object> row, LookupGroup group) {
        if (!group.ignoreEmpty()) {
            row.put(group.rootField(), group.toMany() ? Collections.emptyList() : null);
        }
    }

    /**
     * Fast path when all values in a lookup group are empty.
     */
    private void handleEmptyAndCleanup(List<Map<String, Object>> rows, LookupGroup group) {
        for (Map<String, Object> row : rows) {
            if (!row.containsKey(FileConstant.FAILED_REASON)) {
                handleEmptyRootField(row, group);
            }
            removeDottedPaths(row, group);
        }
    }

    /**
     * Extracts one business-key tuple from a row for a to-one lookup group.
     *
     * @return the key tuple, or {@code null} when all source fields are empty
     */
    private List<Object> extractKeyValues(Map<String, Object> row, LookupGroup group) {
        List<Object> values = new ArrayList<>(group.dottedPaths().size());
        boolean allEmpty = true;
        for (String dottedPath : group.dottedPaths()) {
            Object val = row.get(dottedPath);
            if (val != null && (!(val instanceof String s) || !s.isBlank())) {
                allEmpty = false;
            }
            values.add(val);
        }
        return allEmpty ? null : values;
    }

    /**
     * Extracts multiple business-key tuples from a row for a to-many lookup group.
     *
     * <p>For a single lookup field, a cell like {@code ADMIN,USER} becomes
     * {@code [["ADMIN"], ["USER"]]}.</p>
     *
     * @return a list of key tuples, or {@code null} when all source fields are empty
     */
    private List<List<Object>> extractToManyKeyValues(Map<String, Object> row, LookupGroup group) {
        List<List<String>> splitColumns = new ArrayList<>(group.dottedPaths().size());
        boolean allEmpty = true;
        int expectedSize = -1;
        for (String dottedPath : group.dottedPaths()) {
            Object rawValue = row.get(dottedPath);
            List<String> values = splitToManyCellValues(rawValue);
            if (!values.isEmpty()) {
                allEmpty = false;
            }
            if (expectedSize == -1) {
                expectedSize = values.size();
            } else if (expectedSize != values.size()) {
                throw new ValidationException(
                        "The relation lookup field `{0}` expects the same number of items in columns {1}, but got {2} and {3}.",
                        group.rootField(), group.dottedPaths(), expectedSize, values.size());
            }
            splitColumns.add(values);
        }
        if (allEmpty) {
            return null;
        }
        List<List<Object>> keyGroups = new ArrayList<>(expectedSize);
        for (int i = 0; i < expectedSize; i++) {
            List<Object> keyValues = new ArrayList<>(splitColumns.size());
            boolean allItemEmpty = true;
            for (List<String> columnValues : splitColumns) {
                String value = columnValues.get(i);
                if (StringUtils.isNotBlank(value)) {
                    allItemEmpty = false;
                }
                keyValues.add(value);
            }
            if (allItemEmpty) {
                throw new ValidationException(
                        "The relation lookup field `{0}` contains an empty item at position {1} in columns {2}.",
                        group.rootField(), i + 1, group.dottedPaths());
            }
            keyGroups.add(keyValues);
        }
        return keyGroups;
    }

    /**
     * Splits a to-many source cell into individual business-key values.
     */
    private List<String> splitToManyCellValues(Object rawValue) {
        if (rawValue == null) {
            return Collections.emptyList();
        }
        if (rawValue instanceof String str) {
            if (str.isBlank()) {
                return Collections.emptyList();
            }
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .toList();
        }
        if (rawValue instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return Collections.emptyList();
            }
            return collection.stream().map(value -> value == null ? "" : value.toString().trim()).toList();
        }
        return List.of(rawValue.toString());
    }

    /**
     * Removes temporary dotted-path fields after resolution.
     */
    private void removeDottedPaths(Map<String, Object> row, LookupGroup group) {
        for (String dottedPath : group.dottedPaths()) {
            row.remove(dottedPath);
        }
    }

    /**
     * Builds a human-readable not-found message for one business-key tuple.
     */
    private String buildNotFoundMessage(LookupGroup group, List<Object> keyValues) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cannot find ").append(group.relatedModel()).append(" by ");
        for (int i = 0; i < group.lookupFields().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(group.lookupFields().get(i)).append("=").append(keyValues.get(i));
        }
        return sb.toString();
    }
}
