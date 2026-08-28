package io.softa.framework.web.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

/**
 * Recompute Utils
 */
public class RecomputeUtils {

    private RecomputeUtils() {}

    /**
     * Get the dependent fields for stored cascaded and computed fields. Reading those is what lets the
     * write pipeline recalculate the fields that derive from them: a stored computed field is recomputed
     * when any of its dependencies is present in the row submitted for update.
     *
     * @param modelName the name of the model
     * @param fields a set of field names that need to be recalculated, all of them when empty
     * @return a set of dependent fields
     */
    public static Set<String> getDependedFields(String modelName, Set<String> fields) {
        Collection<MetaField> metaFields;
        if (CollectionUtils.isEmpty(fields)) {
            metaFields = ModelManager.getModelFields(modelName);
        } else {
            metaFields = fields.stream().map(field -> ModelManager.getModelField(modelName, field)).collect(Collectors.toList());
        }
        // Get the dependent fields for stored cascaded and computed fields
        Set<String> dependedFields = new HashSet<>();
        metaFields.stream()
                .filter(metaField -> !metaField.isDynamic())
                .forEach(metaField -> {
                    if (StringUtils.isNotBlank(metaField.getCascadedField())) {
                        dependedFields.add(metaField.getDependentFields().getFirst());
                    } else if (metaField.isComputed()) {
                        dependedFields.addAll(metaField.getDependentFields());
                    }
                });
        return dependedFields;
    }

}
