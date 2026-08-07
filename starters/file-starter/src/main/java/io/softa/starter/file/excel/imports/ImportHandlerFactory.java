package io.softa.starter.file.excel.imports;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.starter.file.dto.ImportFieldDTO;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.excel.imports.handler.*;

@Component
public class ImportHandlerFactory {

    /**
     * Create the field handlers for the import template.
     * Dotted-path <b>relation lookup</b> fields (e.g. deptId.code) are skipped here because they are
     * resolved by {@link RelationLookupResolver} against the related model's business key. Dotted-path
     * <b>OneToOne</b> fields are different: they describe the main row's own sub-record and are written
     * inline, so they still need a handler — see {@link #createNestedOneToOneHandler}.
     */
    public List<BaseImportHandler> createHandlers(ImportTemplateDTO importTemplateDTO) {
        String modelName = importTemplateDTO.getModelName();
        List<BaseImportHandler> handlers = new ArrayList<>();
        for (ImportFieldDTO importFieldDTO : importTemplateDTO.getImportFields()) {
            String fieldName = importFieldDTO.getFieldName();
            if (fieldName.contains(".")) {
                BaseImportHandler nestedHandler = createNestedOneToOneHandler(modelName, fieldName, importFieldDTO);
                if (nestedHandler != null) {
                    handlers.add(nestedHandler);
                }
                continue;
            }
            if (!ModelManager.existField(modelName, fieldName)) {
                continue;
            }
            MetaField metaField = ModelManager.getModelField(modelName, fieldName);
            if (!Boolean.TRUE.equals(importFieldDTO.getRequired())) {
                importFieldDTO.setRequired(metaField.isRequired());
            }
            handlers.add(createHandler(metaField, importFieldDTO));
        }
        return handlers;
    }

    /**
     * Create a handler for a nested OneToOne sub-field, e.g. {@code employeeProfileId.gender}.
     *
     * <p>A OneToOne dotted path is not a lookup: the related row is the main row's own 1:1 sub-record,
     * written inline by the ORM cascade. Its cells therefore still need the standard conversion the
     * flat columns get — dates parsed, options mapped — which the lookup path used to supply as a side
     * effect of normalizing the business key. The metadata comes from the <i>related</i> model while
     * the column stays keyed by the dotted path, hence {@link BaseImportHandler#rowKey}.
     *
     * <p>{@code required} is deliberately NOT inherited from the sub-field's metadata: a blank cell on
     * a OneToOne sub-field means "leave this value alone" on update, and the ORM still enforces the
     * requirement when the sub-record is created.
     *
     * @return the handler keyed by the dotted path, or null when the path is a relation lookup instead
     */
    BaseImportHandler createNestedOneToOneHandler(String modelName, String fieldName, ImportFieldDTO importFieldDTO) {
        String[] parts = fieldName.split("\\.");
        if (parts.length != 2 || !ModelManager.existField(modelName, parts[0])) {
            return null;
        }
        MetaField rootMetaField = ModelManager.getModelField(modelName, parts[0]);
        // A blank relatedModel is a misconfigured template: fall through so RelationLookupResolver
        // reports it by name, rather than failing here on an unknown model.
        if (!FieldType.ONE_TO_ONE.equals(rootMetaField.getFieldType())
                || StringUtils.isBlank(rootMetaField.getRelatedModel())
                || !ModelManager.existField(rootMetaField.getRelatedModel(), parts[1])) {
            return null;
        }
        MetaField subMetaField = ModelManager.getModelField(rootMetaField.getRelatedModel(), parts[1]);
        return createHandler(subMetaField, importFieldDTO).rowKey(fieldName);
    }

    BaseImportHandler createHandler(MetaField metaField, ImportFieldDTO importFieldDTO) {
        return switch (metaField.getFieldType()) {
            case BOOLEAN -> new BooleanHandler(metaField, importFieldDTO);
            case DATE -> new DateHandler(metaField, importFieldDTO);
            case DATE_TIME -> new DateTimeHandler(metaField, importFieldDTO);
            case TIME -> new TimeHandler(metaField, importFieldDTO);
            case MULTI_OPTION -> new MultiOptionHandler(metaField, importFieldDTO);
            case OPTION -> new OptionHandler(metaField, importFieldDTO);
            default -> new DefaultHandler(metaField, importFieldDTO);
        };
    }
}
