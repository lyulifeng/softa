package io.softa.starter.file.excel.export.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.softa.framework.orm.domain.FilterUnit;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaOptionItem;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.meta.OptionManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportFieldDTO;

/**
 * Works out which template columns can offer a fixed list of values, and what those values are.
 *
 * <p>Two kinds of column qualify, and they are answered from different places:
 *
 * <ul>
 *   <li>an {@code OPTION} / {@code MULTI_OPTION} field, whose set is platform metadata — read from
 *       {@link OptionManager}, which is an in-memory cache, so this costs nothing;
 *   <li>a relation onto {@code TenantOptionItem} addressed as {@code someField.itemCode}, whose items
 *       are tenant data and have to be queried. All such sets are fetched in one query rather than one
 *       per column.
 * </ul>
 *
 * <p>Values are item codes, matching what the import side resolves a {@code .itemCode} column by, so a
 * template filled from its own dropdown imports without translation.
 *
 * <p>The tenant-option query is narrowed by the field's own {@code filters}, which is where a relation
 * onto {@code TenantOptionItem} names its set ({@code ["optionSetCode","=","OrganizationType"]}).
 * Without that narrowing the list would be every option item the tenant owns, across every set.
 */
@Slf4j
@Component
public class OptionDropdownResolver {

    /** The model that holds per-tenant option items, referenced by name because it lives in another starter. */
    private static final String TENANT_OPTION_ITEM = "TenantOptionItem";

    private static final String ITEM_CODE = "itemCode";
    private static final String OPTION_SET_CODE = "optionSetCode";
    private static final String SEQUENCE = "sequence";

    @Autowired
    private ModelService<?> modelService;

    /**
     * @param modelName    the model the template imports into
     * @param importFields the template's columns, in the order they appear on the sheet
     * @return column index (0-based) → allowed item codes; columns with no fixed list are absent
     */
    public Map<Integer, List<String>> resolve(String modelName, List<ImportFieldDTO> importFields) {
        Map<Integer, List<String>> optionsByColumn = new LinkedHashMap<>();
        // optionSetCode → the columns waiting on it, so one query serves however many columns share a set
        Map<String, List<Integer>> tenantSetToColumns = new LinkedHashMap<>();

        for (int columnIndex = 0; columnIndex < importFields.size(); columnIndex++) {
            String fieldName = importFields.get(columnIndex).getFieldName();
            if (StringUtils.isBlank(fieldName)) {
                continue;
            }
            try {
                if (fieldName.contains(".")) {
                    String tenantOptionSet = tenantOptionSetOf(modelName, fieldName);
                    if (tenantOptionSet != null) {
                        tenantSetToColumns.computeIfAbsent(tenantOptionSet, k -> new ArrayList<>()).add(columnIndex);
                    }
                } else {
                    List<String> codes = platformOptionCodesOf(modelName, fieldName);
                    if (!codes.isEmpty()) {
                        optionsByColumn.put(columnIndex, codes);
                    }
                }
            } catch (RuntimeException e) {
                // A column whose metadata cannot be read simply gets no dropdown. Kept quiet at debug
                // level: templates legitimately carry columns that are not fields at all.
                log.debug("No dropdown for column '{}' of model {}: {}", fieldName, modelName, e.getMessage());
            }
        }
        if (!tenantSetToColumns.isEmpty()) {
            Map<String, List<String>> codesBySet = queryTenantOptionCodes(tenantSetToColumns.keySet());
            tenantSetToColumns.forEach((optionSetCode, columns) -> {
                List<String> codes = codesBySet.get(optionSetCode);
                if (codes != null && !codes.isEmpty()) {
                    columns.forEach(columnIndex -> optionsByColumn.put(columnIndex, codes));
                }
            });
        }
        return optionsByColumn;
    }

    /** Item codes of a platform option set, or empty when the field is not option-backed. */
    private List<String> platformOptionCodesOf(String modelName, String fieldName) {
        MetaField metaField = ModelManager.getModelFieldOrNull(modelName, fieldName);
        if (metaField == null) {
            return List.of();
        }
        FieldType fieldType = metaField.getFieldType();
        if (fieldType != FieldType.OPTION && fieldType != FieldType.MULTI_OPTION) {
            return List.of();
        }
        String optionSetCode = metaField.getOptionSetCode();
        if (StringUtils.isBlank(optionSetCode) || !OptionManager.existsOptionSetCode(optionSetCode)) {
            return List.of();
        }
        return OptionManager.getMetaOptionItems(optionSetCode).stream()
                .map(MetaOptionItem::getItemCode)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /**
     * The option set behind a {@code someField.itemCode} column, or null when the column is not a
     * TenantOptionItem lookup addressed by item code.
     */
    private String tenantOptionSetOf(String modelName, String dottedFieldName) {
        String[] parts = dottedFieldName.split("\\.");
        if (parts.length != 2 || !ITEM_CODE.equals(parts[1])) {
            return null;
        }
        MetaField rootField = ModelManager.getModelFieldOrNull(modelName, parts[0]);
        if (rootField == null || !TENANT_OPTION_ITEM.equals(rootField.getRelatedModel())) {
            return null;
        }
        // The set is named in the field's own filters; without it the list would span every set.
        return optionSetCodeIn(Filters.of(rootField.getFilters()));
    }

    /**
     * Pulls the {@code optionSetCode} value out of a filter tree.
     *
     * <p>Walks it rather than reading a fixed position: the field declares a single equality today, but
     * a filter is a tree and a second condition would push the one that matters out of place.
     */
    private String optionSetCodeIn(Filters filters) {
        if (filters == null) {
            return null;
        }
        FilterUnit unit = filters.getFilterUnit();
        if (unit != null && OPTION_SET_CODE.equals(unit.getField()) && unit.getValue() != null) {
            String value = String.valueOf(unit.getValue());
            return StringUtils.isBlank(value) ? null : value;
        }
        if (filters.getChildren() != null) {
            for (Filters child : filters.getChildren()) {
                String found = optionSetCodeIn(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** One query for every set the template needs, keyed back by set code. */
    private Map<String, List<String>> queryTenantOptionCodes(Iterable<String> optionSetCodes) {
        List<String> codes = new ArrayList<>();
        optionSetCodes.forEach(codes::add);
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            // Ordered by the sequence the set itself defines, so the dropdown reads in the same order
            // as the field's picker elsewhere in the product. Unordered it would follow whatever the
            // database happened to return.
            FlexQuery flexQuery = new FlexQuery(
                    List.of(OPTION_SET_CODE, ITEM_CODE),
                    new Filters().in(OPTION_SET_CODE, codes),
                    Orders.ofAsc(OPTION_SET_CODE).addAsc(SEQUENCE));
            List<Map<String, Object>> rows = modelService.searchList(TENANT_OPTION_ITEM, flexQuery);
            for (Map<String, Object> row : rows) {
                Object setCode = row.get(OPTION_SET_CODE);
                Object itemCode = row.get(ITEM_CODE);
                if (setCode == null || itemCode == null) {
                    continue;
                }
                result.computeIfAbsent(String.valueOf(setCode), k -> new ArrayList<>())
                        .add(String.valueOf(itemCode));
            }
        } catch (RuntimeException e) {
            // Tenant option data being unreadable costs the dropdowns, not the template.
            log.warn("Could not read tenant option items for {}, those columns get no dropdown: {}",
                    codes, e.getMessage());
        }
        return result;
    }
}
