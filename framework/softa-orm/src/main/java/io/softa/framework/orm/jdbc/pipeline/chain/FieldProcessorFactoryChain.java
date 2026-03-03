package io.softa.framework.orm.jdbc.pipeline.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.enums.AccessType;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.jdbc.pipeline.factory.FieldProcessorFactory;
import io.softa.framework.orm.jdbc.pipeline.factory.XToOneGroupProcessorFactory;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

/**
 * Field processor factory chain object. The fieldProcessorFactory need to be added in different orders according to
 * the CREATE, UPDATE, and READ scenarios.
 * The final fieldProcessorChain is generated according to the order of fieldProcessorFactory classes  and the fields
 * to be processed.
 */
public class FieldProcessorFactoryChain {
    private final String modelName;
    private final AccessType accessType;
    private final List<FieldProcessorFactory> processorFactories = new ArrayList<>();

    private FieldProcessorFactoryChain(String modelName, AccessType accessType) {
        this.modelName = modelName;
        this.accessType = accessType;
    }

    /**
     * Static factory chain construction method.
     *
     * @param modelName model name
     * @return factory chain
     */
    public static FieldProcessorFactoryChain of(String modelName, AccessType accessType) {
        return new FieldProcessorFactoryChain(modelName, accessType);
    }

    public FieldProcessorFactoryChain addFactory(FieldProcessorFactory processorFactory) {
        processorFactories.add(processorFactory);
        return this;
    }

    /**
     * Generate the fieldProcessorChain according to the processorFactories and fields.
     *
     * @param fields fields to be processed
     * @return fieldProcessorChain
     */
    public FieldProcessorChain generateProcessorChain(Set<String> fields) {
        FieldProcessorChain fieldProcessorChain = new FieldProcessorChain();
        for (FieldProcessorFactory processorFactory : processorFactories) {
            for (String field : fields) {
                // Custom virtual cascaded field doesn't exist in metadata, so create a metaField object here
                // for processing.
                // TODO: Need to use other solutions to replace this.
                if (field.contains(".")) {
                    if (processorFactory instanceof XToOneGroupProcessorFactory) {
                        MetaField metaField = this.createCustomCascadedField(field);
                        fieldProcessorChain.addProcessor(processorFactory.createProcessor(metaField, accessType));
                    }
                } else {
                    MetaField metaField = ModelManager.getModelField(modelName, field);
                    // Dynamic cascaded field, already processed in the associated model, only fetched by XToOneGroupProcessorFactory here.
                    if (metaField.isDynamicCascadedField() && !(processorFactory instanceof XToOneGroupProcessorFactory)) {
                        continue;
                    }
                    fieldProcessorChain.addProcessor(processorFactory.createProcessor(metaField, accessType));
                }
            }
        }
        return fieldProcessorChain;
    }

    /**
     * Initialize the custom virtual cascaded fields.
     *
     * @param fieldName custom cascaded field name like `a.b.c`
     */
    @Deprecated
    private MetaField createCustomCascadedField(String fieldName) {
        String[] fieldsArray = StringUtils.split(fieldName, ".");
        Assert.isTrue(fieldsArray.length - 1 <= BaseConstant.CASCADE_LEVEL, """
                        Due to database performance considerations, obtain the model cascaded field {0}: {1},
                        the cascade level cannot exceed the {2} level.""",
                modelName, fieldName, BaseConstant.CASCADE_LEVEL);
        return MetaField.createDynamicField(modelName, fieldName);
    }
}
