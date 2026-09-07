package io.softa.starter.file.excel.export.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.domain.FilterUnit;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.MetaOptionItem;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.meta.OptionManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportFieldDTO;

/**
 * Works out which template columns can offer a fixed list of values, and what those values are.
 *
 * <p>A column qualifies when its value comes from a set someone could have picked from in the product.
 * Four shapes of column do, and they are answered from different places:
 *
 * <ul>
 *   <li><b>An option field</b> ({@code OPTION} / {@code MULTI_OPTION}) — its set is platform metadata,
 *       read from {@link OptionManager}, an in-memory cache, so this costs nothing.
 *   <li><b>A boolean field</b> — the same two labels the export writes, taken from the platform's own
 *       boolean option set rather than spelled out here, so the two sides cannot drift apart.
 *   <li><b>{@code someRelation.itemCode}</b> onto {@code TenantOptionItem} — tenant data, so it has to
 *       be queried. Every such set the template needs is fetched in one query, not one per column.
 *   <li><b>{@code someRelation.someField}</b> onto any other model — the values are that model's own
 *       {@code someField} column. This is the general case and by far the largest: a template that
 *       picks a bank, a job grade, a pass type or a country addresses it this way.
 * </ul>
 *
 * <p>Under the last shape the relation's target may itself be an option or boolean field rather than a
 * stored scalar — {@code employeeProfileId.gender} reaches through a one-to-one into an enum. Those are
 * answered from metadata without a query, the same as if the field had been addressed directly. Not
 * reaching through was why templates built on a one-to-one sub-record carried no dropdowns at all.
 *
 * <p><b>Values match what the import side accepts back.</b> An option column offers item codes, which
 * {@code OptionHandler} resolves; a relation column offers the very field the column names, which
 * {@code RelationLookupResolver} reverse-looks-up to an id. So a template filled from its own dropdown
 * imports without translation.
 *
 * <p><b>A bare relation column gets no dropdown.</b> Addressed without a dotted path, the cell holds a
 * raw foreign key, and a list of ids is not something anyone can pick from. Such a column is left
 * alone rather than offered a list of the target's labels, which would look right and fail on import.
 *
 * <p><b>The country is passed in, not inferred.</b> Models that vary by country are narrowed by the
 * request's own country everywhere else in the product — but a template declares the country it is
 * for, and an administrator of a Singapore company may quite legitimately download the New Zealand
 * template. Inferring it there would fill an NZ template with SG values.
 */
@Slf4j
@Component
public class OptionDropdownResolver {

    /** The model that holds per-tenant option items, referenced by name because it lives in another starter. */
    private static final String TENANT_OPTION_ITEM = "TenantOptionItem";

    private static final String ITEM_CODE = "itemCode";
    private static final String OPTION_SET_CODE = "optionSetCode";
    private static final String SEQUENCE = "sequence";

    /** The field a country-scoped model is narrowed by. Fixed platform-wide, the same one the ORM uses. */
    private static final String COUNTRY = "country";

    /**
     * Most values a single column will offer. A dropdown longer than this has stopped being a way to
     * choose and become a way to scroll, and the hidden sheet backing it grows a row per value. The
     * largest set any template points at today is the country list, at roughly 250.
     */
    private static final int MAX_VALUES_PER_COLUMN = 1000;

    @Autowired
    private ModelService<?> modelService;

    /**
     * One batch of values to fetch from an ordinary model.
     *
     * <p>A record so that columns asking for the same thing — two project-team columns, say — collapse
     * to a single query by map key rather than by any comparison written out here.
     *
     * @param modelName the model holding the values
     * @param fieldName the field whose values the column offers
     * @param filters   the root field's own {@code filters}, verbatim, so two columns onto the same
     *                  model narrowed differently stay separate requests
     * @param country   the country to narrow by, or null when the model does not vary by country
     */
    private record ValueRequest(String modelName, String fieldName, String filters, String country) {
    }

    /**
     * A column whose values are narrowed by another column of the same sheet.
     *
     * @param parentColumn          the column index the reader picks first
     * @param valuesByParentValue   parent value → the values this column may then offer, in the order
     *                              the flat list had them; a parent with no children is absent
     */
    public record Cascade(int parentColumn, Map<String, List<String>> valuesByParentValue) {
    }

    /**
     * @param modelName    the model the template imports into
     * @param importFields the template's columns, in the order they appear on the sheet
     * @param country      the country the template is for, or blank for a template that applies
     *                     everywhere; narrows the country-scoped models a column may point at
     * @return column index (0-based) → allowed values; columns with no fixed list are absent
     */
    public Map<Integer, List<String>> resolve(String modelName, List<ImportFieldDTO> importFields, String country) {
        return resolveAll(modelName, importFields, country).optionsByColumn();
    }

    /**
     * Everything the sheet needs: the values per column, and which of those columns are narrowed by
     * another column of the same sheet.
     *
     * @param modelName    the model the template imports into
     * @param importFields the template's columns, in the order they appear on the sheet
     * @param country      the country the template is for, or blank for a template that applies
     *                     everywhere
     */
    public Resolution resolveAll(String modelName, List<ImportFieldDTO> importFields, String country) {
        Map<Integer, List<String>> optionsByColumn = new LinkedHashMap<>();
        // what each column's values were asked for, so a parent/child pair can be spotted afterwards
        Map<Integer, ValueRequest> requestByColumn = new LinkedHashMap<>();
        // optionSetCode → the columns waiting on it, so one query serves however many columns share a set
        Map<String, List<Integer>> tenantSetToColumns = new LinkedHashMap<>();
        // and the same idea for ordinary models, keyed by the whole request
        Map<ValueRequest, List<Integer>> entityRequestToColumns = new LinkedHashMap<>();

        for (int columnIndex = 0; columnIndex < importFields.size(); columnIndex++) {
            ImportFieldDTO importField = importFields.get(columnIndex);
            String fieldName = importField.getFieldName();
            if (StringUtils.isBlank(fieldName)) {
                continue;
            }
            if (Boolean.TRUE.equals(importField.getNoDropdown())) {
                // The template says this column is typed, not picked. Inference would say otherwise —
                // it reads metadata, which cannot tell a dictionary from a table of people.
                continue;
            }
            try {
                if (fieldName.contains(".")) {
                    resolveDottedColumn(modelName, fieldName, country, columnIndex,
                            optionsByColumn, tenantSetToColumns, entityRequestToColumns);
                } else {
                    List<String> values = metadataValuesOf(modelName, fieldName);
                    if (!values.isEmpty()) {
                        optionsByColumn.put(columnIndex, values);
                    } else {
                        queueCodeAsIdRequest(ModelManager.getModelFieldOrNull(modelName, fieldName),
                                country, columnIndex, entityRequestToColumns);
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
            tenantSetToColumns.forEach((optionSetCode, columns) ->
                    assign(optionsByColumn, columns, codesBySet.get(optionSetCode)));
        }
        entityRequestToColumns.forEach((request, columns) -> {
            assign(optionsByColumn, columns, queryEntityValues(request));
            columns.forEach(columnIndex -> requestByColumn.put(columnIndex, request));
        });
        return new Resolution(optionsByColumn, resolveCascades(requestByColumn, country));
    }

    /**
     * Everything resolved for one sheet.
     *
     * @param optionsByColumn   column index → the values it may offer
     * @param cascadesByColumn  column index → the column it is narrowed by, for the few that are
     */
    public record Resolution(Map<Integer, List<String>> optionsByColumn, Map<Integer, Cascade> cascadesByColumn) {
    }

    /**
     * Finds the columns of a sheet that narrow one another.
     *
     * <p>Two columns form a pair when the child's model carries a many-to-one onto the parent's model:
     * an education track names the level it belongs to, so the tracks worth offering are the ones for
     * the level already chosen rather than every track in the country.
     *
     * <p><b>Only between two code-as-id columns.</b> Both then offer ids, and the child's foreign key
     * holds exactly the value the parent column offers — so the grouping is one query and no
     * translation. A pair where either side is addressed by name would need the parent's ids and names
     * matched up as well; that is a further step, and no template asks for it yet.
     */
    private Map<Integer, Cascade> resolveCascades(Map<Integer, ValueRequest> requestByColumn, String country) {
        Map<Integer, Cascade> cascades = new LinkedHashMap<>();
        requestByColumn.forEach((columnIndex, child) -> {
            for (Map.Entry<Integer, ValueRequest> candidate : requestByColumn.entrySet()) {
                if (candidate.getKey().equals(columnIndex)) {
                    continue;
                }
                ValueRequest parent = candidate.getValue();
                boolean bothAddressedById = ModelConstant.ID.equals(child.fieldName())
                        && ModelConstant.ID.equals(parent.fieldName());
                MetaField link = linkFieldOnto(child.modelName(), parent.modelName(), bothAddressedById);
                if (link == null) {
                    continue;
                }
                Map<String, List<String>> grouped = queryGroupedByParent(child, parent, link.getFieldName(), country);
                if (!grouped.isEmpty()) {
                    cascades.put(columnIndex, new Cascade(candidate.getKey(), grouped));
                }
                return;
            }
        });
        return cascades;
    }

    /** The many-to-one on {@code childModel} that points at {@code parentModel}, or null when none does. */
    /**
     * The many-to-one on {@code childModel} that makes {@code parentModel} its cascade parent, or null.
     *
     * <p>Two rules, and which applies is the child model's choice. A model that flags any of its
     * relations with {@code @Field(cascadeParent = true)} has said which of its foreign keys are
     * hierarchy and which are not — only a flagged one onto this parent pairs, however the two columns
     * are addressed. A model that flags nothing gets the pre-declaration rule unchanged: both columns
     * code-as-id, first many-to-one onto the parent wins. That keeps every template that works today
     * working, and lets a model opt in without a flag day.
     *
     * <p>The distinction the flag draws is one structure cannot: a track points at its level and at its
     * country with the same kind of field, and only one of those is a parent to narrow by. Read every
     * many-to-one as a parent and the nationality column starts narrowing the race column.
     */
    private MetaField linkFieldOnto(String childModel, String parentModel, boolean bothAddressedById) {
        if (!ModelManager.existModel(childModel)) {
            return null;
        }
        List<MetaField> childFields = ModelManager.getModelFields(childModel);
        List<MetaField> ontoParent = childFields.stream()
                .filter(f -> f.getFieldType() == FieldType.MANY_TO_ONE)
                .filter(f -> parentModel.equals(f.getRelatedModel()))
                .toList();
        boolean childDeclaresParents = childFields.stream().anyMatch(MetaField::isCascadeParent);
        if (childDeclaresParents) {
            return ontoParent.stream().filter(MetaField::isCascadeParent).findFirst().orElse(null);
        }
        return bothAddressedById ? ontoParent.stream().findFirst().orElse(null) : null;
    }

    /** The child model's ids, grouped under the parent id each one names. */
    private Map<String, List<String>> queryGroupedByParent(ValueRequest child, ValueRequest parent,
                                                           String linkField, String country) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        try {
            Filters filters = new Filters();
            Filters declared = Filters.of(child.filters());
            if (!Filters.isEmpty(declared)) {
                filters.and(declared);
            }
            if (narrowingCountryFor(child.modelName(), country) != null) {
                filters.and(COUNTRY, Operator.EQUAL, country);
            }
            String childValue = child.fieldName();
            FlexQuery flexQuery = new FlexQuery(List.of(childValue, linkField), filters,
                    Orders.ofAsc(childValue));
            flexQuery.setLimitSize(MAX_VALUES_PER_COLUMN + 1);
            List<Map<String, Object>> rows = modelService.searchList(child.modelName(), flexQuery);
            if (rows.size() > MAX_VALUES_PER_COLUMN) {
                // Said out loud, as the flat list does. The cut falls at the end of the ordering, so
                // the parents that lose their children are whichever sort last — and on the sheet that
                // looks like a level with no tracks rather than like a list that was truncated.
                log.warn("{} has more than {} rows to group by {}; the later parents will offer nothing.",
                        child.modelName(), MAX_VALUES_PER_COLUMN, linkField);
                rows = rows.subList(0, MAX_VALUES_PER_COLUMN);
            }
            // The link column holds the parent's id, but the sheet matches the parent CELL — which shows
            // whatever the parent column is addressed by. Group under that, or MATCH never finds it.
            Map<String, String> parentKeyById = parentKeysById(parent, rows, linkField);
            if (parentKeyById == null) {
                return grouped;
            }
            for (Map<String, Object> row : rows) {
                Object value = row.get(childValue);
                Object parentId = row.get(linkField);
                if (value == null || parentId == null) {
                    continue;
                }
                String parentKey = parentKeyById.get(String.valueOf(parentId));
                if (parentKey == null) {
                    // A parent the parent column does not offer (filtered out, another country): its
                    // children have no cell to hang off, so they are not offered either.
                    continue;
                }
                grouped.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(String.valueOf(value));
            }
        } catch (RuntimeException e) {
            // Losing the grouping costs the narrowing, not the column: it keeps the flat list it
            // already resolved to.
            log.warn("Could not group {} by {}, that column stays a flat list: {}",
                    child.modelName(), linkField, e.getMessage());
        }
        return grouped;
    }

    /**
     * Parent id → the value the parent column offers for that row. Identity when the parent column is
     * addressed by id (the pre-declaration shape: nothing to translate, no query). Otherwise one query
     * on the parent model, under the same filters and country narrowing the parent's own list used, so
     * the keys are drawn from exactly the values the parent cell can hold.
     *
     * <p>Null when two parents would share a key. The sheet finds a parent by matching the cell's text
     * against the list of keys, so two rows with the same name are one position — whichever came second
     * would silently offer the first one's children. A flat list is the honest fallback.
     */
    private Map<String, String> parentKeysById(ValueRequest parent, List<Map<String, Object>> childRows,
                                               String linkField) {
        Set<String> parentIds = new LinkedHashSet<>();
        for (Map<String, Object> row : childRows) {
            Object parentId = row.get(linkField);
            if (parentId != null) {
                parentIds.add(String.valueOf(parentId));
            }
        }
        Map<String, String> keyById = new LinkedHashMap<>();
        if (ModelConstant.ID.equals(parent.fieldName())) {
            parentIds.forEach(id -> keyById.put(id, id));
            return keyById;
        }
        Filters filters = new Filters().in(ModelConstant.ID, new ArrayList<>(parentIds));
        Filters declared = Filters.of(parent.filters());
        if (!Filters.isEmpty(declared)) {
            filters.and(declared);
        }
        if (parent.country() != null) {
            filters.and(COUNTRY, Operator.EQUAL, parent.country());
        }
        FlexQuery flexQuery = new FlexQuery(List.of(ModelConstant.ID, parent.fieldName()), filters,
                Orders.ofAsc(parent.fieldName()));
        List<Map<String, Object>> rows = modelService.searchList(parent.modelName(), flexQuery);
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get(ModelConstant.ID);
            Object key = row.get(parent.fieldName());
            if (id == null || key == null) {
                continue;
            }
            String parentKey = String.valueOf(key);
            if (!seen.add(parentKey)) {
                log.warn("Two {} rows offer the same {} '{}'; a dependent column cannot tell them apart "
                                + "and stays a flat list.", parent.modelName(), parent.fieldName(), parentKey);
                return null;
            }
            keyById.put(String.valueOf(id), parentKey);
        }
        return keyById;
    }

    /** Gives every column of a batch the values that batch resolved to, if it resolved to any. */
    private static void assign(Map<Integer, List<String>> optionsByColumn, List<Integer> columns, List<String> values) {
        if (values != null && !values.isEmpty()) {
            columns.forEach(columnIndex -> optionsByColumn.put(columnIndex, values));
        }
    }

    /**
     * Places a {@code root.leaf} column into whichever bucket answers it.
     *
     * <p>Only one level of cascade is considered, matching what the import side accepts: a path with a
     * second dot is rejected there outright, so offering it a dropdown would be offering values for a
     * column that cannot import.
     */
    private void resolveDottedColumn(String modelName, String dottedFieldName, String country, int columnIndex,
                                     Map<Integer, List<String>> optionsByColumn,
                                     Map<String, List<Integer>> tenantSetToColumns,
                                     Map<ValueRequest, List<Integer>> entityRequestToColumns) {
        String[] parts = dottedFieldName.split("\\.");
        if (parts.length == 3) {
            resolveNestedDottedColumn(modelName, parts, country, columnIndex,
                    optionsByColumn, entityRequestToColumns);
            return;
        }
        if (parts.length != 2) {
            return;
        }
        MetaField rootField = ModelManager.getModelFieldOrNull(modelName, parts[0]);
        if (rootField == null
                || !FieldType.RELATED_TYPES.contains(rootField.getFieldType())
                || StringUtils.isBlank(rootField.getRelatedModel())) {
            return;
        }
        String relatedModel = rootField.getRelatedModel();
        String leafField = parts[1];

        if (TENANT_OPTION_ITEM.equals(relatedModel) && ITEM_CODE.equals(leafField)) {
            // The set is named in the field's own filters; without it the list would span every set.
            String optionSetCode = optionSetCodeIn(Filters.of(rootField.getFilters()));
            if (optionSetCode != null) {
                tenantSetToColumns.computeIfAbsent(optionSetCode, k -> new ArrayList<>()).add(columnIndex);
            }
            return;
        }
        // The leaf may be option- or boolean-backed in its own right — reaching through a one-to-one
        // into an enum. Answerable from metadata, so it never becomes a query.
        List<String> fromMetadata = metadataValuesOf(relatedModel, leafField);
        if (!fromMetadata.isEmpty()) {
            optionsByColumn.put(columnIndex, fromMetadata);
            return;
        }
        MetaField leafMetaField = ModelManager.getModelFieldOrNull(relatedModel, leafField);
        if (leafMetaField == null) {
            return;
        }
        if (FieldType.RELATED_TYPES.contains(leafMetaField.getFieldType())) {
            // The leaf is a relation of its own. Addressing through it would be a second hop the import
            // side will not follow — but the cell does not have to hold a name to be useful: it holds
            // the foreign key, and for a code-as-id model that key is the code itself.
            queueCodeAsIdRequest(leafMetaField, country, columnIndex, entityRequestToColumns);
            return;
        }
        if (rootField.getFieldType() == FieldType.ONE_TO_ONE) {
            // Asking the target for the values of this field would be asking every OTHER employee what
            // theirs is. A one-to-one target holds one row per parent row, so its columns are not a set
            // anyone chooses from — they are individual people's data, and a template is a file handed
            // to whoever may download one. On the employee template this branch reached personal email,
            // personal phone and the ID number.
            //
            // The same rule the code-as-id path already states, applied to plain leaves too: a
            // one-to-one is the row's own sub-record, and a sub-record is not picked from a list. What
            // IS a set still comes through above — an option field answers from metadata, a relation
            // offers its target's code-as-ids — because those exist independently of any employee.
            return;
        }
        ValueRequest request = new ValueRequest(relatedModel, leafField, rootField.getFilters(),
                narrowingCountryFor(relatedModel, country));
        entityRequestToColumns.computeIfAbsent(request, k -> new ArrayList<>()).add(columnIndex);
    }

    /**
     * A three-segment column: through the row's own one-to-one sub-record, onto a relation inside it,
     * addressed by a business field — {@code employeeProfileId.idType.name}.
     *
     * <p>Mirrors what the import side accepts, and only that: root one-to-one, middle many-to-one,
     * leaf a plain field on its target. The dropdown offers the leaf's values from the target model —
     * names, for every column this was built for — narrowed by the template's country the same way a
     * two-segment entity column is. Anything shaped differently gets nothing, exactly as it would
     * fail on the way back in.
     */
    private void resolveNestedDottedColumn(String modelName, String[] parts, String country,
                                           int columnIndex,
                                           Map<Integer, List<String>> optionsByColumn,
                                           Map<ValueRequest, List<Integer>> entityRequestToColumns) {
        MetaField rootField = ModelManager.getModelFieldOrNull(modelName, parts[0]);
        if (rootField == null || rootField.getFieldType() != FieldType.ONE_TO_ONE
                || StringUtils.isBlank(rootField.getRelatedModel())) {
            return;
        }
        MetaField nestedField = ModelManager.getModelFieldOrNull(rootField.getRelatedModel(), parts[1]);
        if (nestedField == null || nestedField.getFieldType() != FieldType.MANY_TO_ONE
                || StringUtils.isBlank(nestedField.getRelatedModel())) {
            return;
        }
        String targetModel = nestedField.getRelatedModel();
        // The leaf may be option- or boolean-backed — answerable from metadata, no query.
        List<String> fromMetadata = metadataValuesOf(targetModel, parts[2]);
        if (!fromMetadata.isEmpty()) {
            optionsByColumn.put(columnIndex, fromMetadata);
            return;
        }
        MetaField leafField = ModelManager.getModelFieldOrNull(targetModel, parts[2]);
        if (leafField == null || FieldType.RELATED_TYPES.contains(leafField.getFieldType())) {
            return;
        }
        ValueRequest request = new ValueRequest(targetModel, parts[2], nestedField.getFilters(),
                narrowingCountryFor(targetModel, country));
        entityRequestToColumns.computeIfAbsent(request, k -> new ArrayList<>()).add(columnIndex);
    }

    /**
     * Offers the ids of a relation's target when those ids are codes people can read.
     *
     * <p>A relation cell holds a foreign key. Usually that is a generated number and a list of them is
     * useless — which is why a relation column normally gets nothing. But a model whose id strategy is
     * {@code EXTERNAL_ID} carries its code as its id ({@code SG_Bachelor}, {@code SG_NRIC}), so the key
     * in the cell already is the value a person would pick. Those columns are exactly the ones the
     * country data models use, and on the employee template they are most of the ones that reach
     * through a one-to-one into the profile.
     *
     * <p>Only many-to-one and many-to-many qualify. Those point at a row that exists already and is
     * shared, so choosing one is what the column is for. A one-to-one is the row's own sub-record and a
     * one-to-many its children; neither is picked from a list.
     */
    private void queueCodeAsIdRequest(MetaField metaField, String country, int columnIndex,
                                      Map<ValueRequest, List<Integer>> entityRequestToColumns) {
        if (metaField == null
                || (metaField.getFieldType() != FieldType.MANY_TO_ONE
                        && metaField.getFieldType() != FieldType.MANY_TO_MANY)) {
            return;
        }
        String relatedModel = metaField.getRelatedModel();
        if (StringUtils.isBlank(relatedModel)
                || !ModelManager.existModel(relatedModel)
                || IdStrategy.EXTERNAL_ID != ModelManager.getIdStrategy(relatedModel)) {
            return;
        }
        ValueRequest request = new ValueRequest(relatedModel, ModelConstant.ID, metaField.getFilters(),
                narrowingCountryFor(relatedModel, country));
        entityRequestToColumns.computeIfAbsent(request, k -> new ArrayList<>()).add(columnIndex);
    }

    /**
     * Values a field carries by virtue of its type alone, with no query: an option set's item codes, or
     * a boolean's two labels. Empty for every other field type.
     */
    private List<String> metadataValuesOf(String modelName, String fieldName) {
        MetaField metaField = ModelManager.getModelFieldOrNull(modelName, fieldName);
        if (metaField == null) {
            return List.of();
        }
        FieldType fieldType = metaField.getFieldType();
        if (fieldType == FieldType.BOOLEAN) {
            // The labels, not `true` / `false`: they are what the export writes and what a person
            // reading the sheet expects. The import handler takes either.
            return optionLabels(BaseConstant.BOOLEAN_OPTION_SET_CODE);
        }
        if (fieldType != FieldType.OPTION && fieldType != FieldType.MULTI_OPTION) {
            return List.of();
        }
        // The labels, not the item codes. Every template's spec asks for the name, and the name is
        // what the reader is choosing between — `SG_PR` next to `SG_CITIZEN` asks someone to know the
        // codes before they can fill the sheet in.
        //
        // Safe on the way back in for two reasons that had to both be true. The import handler takes
        // either — it tries the value as an item code first and falls back to reading it as a label.
        // And labels are unique within their set, which is what the new index on sys_option_item
        // guarantees; without it two items could share a label and the lookup would be a coin toss.
        return optionLabels(metaField.getOptionSetCode());
    }

    /** Item codes of a platform option set, empty when the set is unknown. */
    private List<String> optionCodes(String optionSetCode) {
        if (StringUtils.isBlank(optionSetCode) || !OptionManager.existsOptionSetCode(optionSetCode)) {
            return List.of();
        }
        return OptionManager.getMetaOptionItems(optionSetCode).stream()
                .map(MetaOptionItem::getItemCode)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /** Labels of a platform option set, empty when the set is unknown. */
    private List<String> optionLabels(String optionSetCode) {
        if (StringUtils.isBlank(optionSetCode) || !OptionManager.existsOptionSetCode(optionSetCode)) {
            return List.of();
        }
        return OptionManager.getMetaOptionItems(optionSetCode).stream()
                .map(MetaOptionItem::getLabel)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /**
     * The country to narrow a model by, or null when narrowing does not apply — either nothing was
     * passed in, or the model does not vary by country and filtering it on a column it lacks would
     * fail the query rather than the dropdown.
     */
    private String narrowingCountryFor(String modelName, String country) {
        if (StringUtils.isBlank(country)) {
            return null;
        }
        MetaModel metaModel = ModelManager.existModel(modelName) ? ModelManager.getModel(modelName) : null;
        if (metaModel == null || !metaModel.isMultiCountry() || !ModelManager.existField(modelName, COUNTRY)) {
            return null;
        }
        return country;
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

    /**
     * The values behind one relation column: distinct, ordered, and narrowed by both the field's own
     * filters and the template's country.
     */
    private List<String> queryEntityValues(ValueRequest request) {
        try {
            Filters filters = new Filters();
            Filters declared = Filters.of(request.filters());
            if (!Filters.isEmpty(declared)) {
                filters.and(declared);
            }
            if (request.country() != null) {
                filters.and(COUNTRY, Operator.EQUAL, request.country());
            }
            FlexQuery flexQuery = new FlexQuery(List.of(request.fieldName()), filters,
                    Orders.ofAsc(request.fieldName()));
            flexQuery.setDistinct(true);
            // One over the cap, so a set that is too long can be reported rather than silently cut.
            flexQuery.setLimitSize(MAX_VALUES_PER_COLUMN + 1);
            List<Map<String, Object>> rows = modelService.searchList(request.modelName(), flexQuery);
            List<String> values = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object value = row.get(request.fieldName());
                if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                    values.add(String.valueOf(value));
                }
            }
            if (values.size() > MAX_VALUES_PER_COLUMN) {
                log.warn("{}.{} has more than {} values; the column offers the first {} and is no longer "
                                + "a complete list.", request.modelName(), request.fieldName(),
                        MAX_VALUES_PER_COLUMN, MAX_VALUES_PER_COLUMN);
                return values.subList(0, MAX_VALUES_PER_COLUMN);
            }
            return values;
        } catch (RuntimeException e) {
            // The same bargain as the tenant options: a column loses its dropdown, the template still
            // downloads.
            log.warn("Could not read {}.{} for a dropdown: {}",
                    request.modelName(), request.fieldName(), e.getMessage());
            return List.of();
        }
    }
}
