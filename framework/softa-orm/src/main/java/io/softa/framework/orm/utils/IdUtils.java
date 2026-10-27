package io.softa.framework.orm.utils;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.softa.framework.base.utils.Cast;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

/**
 * ID primary field format, mainly used for an id field type is Long,
 * the generic interface parameter may be String or Integer, need to convert the id parameter to Long type.
 */
public class IdUtils {

    private IdUtils() {}

    /**
     * Convert object id to Long.
     *
     * @param id object
     * @return Long
     */
    public static Long convertIdToLong(Object id) {
        if (id instanceof Integer intId) {
            return intId.longValue();
        } else if (id instanceof String stringId) {
            return Long.parseLong(stringId);
        } else {
            return (Long) id;
        }
    }

    /**
     * Convert object id to String.
     *
     * @param id object
     * @return String
     */
    public static String convertIdToString(Object id) {
        if (id instanceof Integer) {
            return String.valueOf(id);
        } else if (id instanceof Long) {
            return String.valueOf(id);
        }
        return (String) id;
    }

    /**
     * Convert List<object> to List<Long>.
     *
     * @param objects List<object>
     * @return List<Long>
     */
    private static List<Long> convertIdsToLong(List<?> objects) {
        return objects.stream().map(IdUtils::convertIdToLong).collect(Collectors.toList());
    }

    /**
     * Convert List<object> to List<String>.
     *
     * @param objects List<object>
     * @return List<String>
     */
    private static List<String> convertIdsToString(List<?> objects) {
        return objects.stream().map(IdUtils::convertIdToString).collect(Collectors.toList());
    }

    /**
     * The type an id held in this field takes: the field's own type, or, for a to-one relation, the
     * type of the id it references.
     *
     * <p>A to-one field's logical type is MANY_TO_ONE / ONE_TO_ONE whatever it stores, so reading
     * that alone left ids arriving as strings — as every id does from a client — unconverted. A
     * many-to-many join model whose sides are relations then compared those strings with the Long
     * ids read back from the join table: a Remove matched nothing and was silently dropped, an Add
     * did not see the link already there and wrote it again.
     *
     * @param modelName model name
     * @param fieldName field name
     * @return the field type an id in this field is converted to
     */
    private static FieldType idTypeOf(String modelName, String fieldName) {
        MetaField field = ModelManager.getModelField(modelName, fieldName);
        if (!FieldType.TO_ONE_TYPES.contains(field.getFieldType())) {
            return field.getFieldType();
        }
        if (field.getRelatedFieldType() != null) {
            return field.getRelatedFieldType();
        }
        return ModelManager.getModelField(field.getRelatedModel(), ModelConstant.ID).getFieldType();
    }

    /**
     * Format ids, when the model primary key field type is Long, convert the id to Long type.
     *
     * @param modelName model name
     * @param ids List<?>
     * @return List<Serializable>
     */
    public static <K extends Serializable> List<K> formatIds(String modelName, String fieldName, List<?> ids) {
        FieldType idType = idTypeOf(modelName, fieldName);
        if (FieldType.LONG.equals(idType)) {
            return Cast.of(convertIdsToLong(ids));
        } else if (FieldType.STRING.equals(idType)) {
            return Cast.of(convertIdsToString(ids));
        } else {
            return Cast.of(ids);
        }
    }

    /**
     * Format ids, when the model primary key field type is Long, convert the id to Long type.
     *
     * @param modelName model name
     * @param ids List<Serializable>
     * @return List<K>
     * @param <K> K
     */
    public static <K extends Serializable> List<K> formatIds(String modelName, List<?> ids) {
        return formatIds(modelName, ModelConstant.ID, ids);
    }

    /**
     * Format single id, when the model primary key field type is Long, convert the id to Long type.
     *
     * @param modelName model name
     * @param field field name
     * @param id id
     * @return K
     * @param <K> K
     */
    public static <K extends Serializable> K formatId(String modelName, String field, Serializable id) {
        if (id == null) {
            return null;
        }
        FieldType fieldType = idTypeOf(modelName, field);
        if (FieldType.LONG.equals(fieldType)) {
            return Cast.of(convertIdToLong(id));
        } else if (FieldType.STRING.equals(fieldType)) {
            return Cast.of(convertIdToString(id));
        } else {
            return Cast.of(id);
        }
    }

    /**
     * Format single id, when the model primary key field type is Long, convert the id to Long type.
     *
     * @param modelName model name
     * @param id id
     * @return K
     * @param <K> K
     */
    public static <K extends Serializable> K formatId(String modelName, Serializable id) {
        return formatId(modelName, ModelConstant.ID, id);
    }

    /**
     * Format id field, when the field type is Long, convert the id to Long type.
     *
     * @param idType id FieldType
     * @param id id
     * @return Object
     */
    public static Object formatId(FieldType idType, Object id) {
        if (FieldType.LONG.equals(idType)) {
            return Cast.of(convertIdToLong(id));
        } else if (FieldType.STRING.equals(idType)) {
            return Cast.of(convertIdToString(id));
        } else {
            return id;
        }
    }

    /**
     * Format the primary key field in the Map.
     * When the model primary key field type is Long, convert the id to Long type.
     *
     * @param modelName model name
     * @param row Map<String, Object>
     */
    public static void formatMapId(String modelName, Map<String, Object> row) {
        MetaField pkField = ModelManager.getModelPrimaryKeyField(modelName);
        String pk = pkField.getFieldName();
        if (FieldType.LONG.equals(pkField.getFieldType())) {
            row.put(pk, convertIdToLong(row.get(pk)));
        } else if (FieldType.STRING.equals(pkField.getFieldType())) {
            row.put(pk, convertIdToString(row.get(pk)));
        }
    }

    /**
     * Format the primary key field in the List<Map>.
     * When the model primary key field type is Long, convert the id to Long type.
     *
     * @param modelName model name
     * @param rows List<Map<String, Object>>
     */
    public static void formatMapIds(String modelName, List<Map<String, Object>> rows) {
        MetaField pkField = ModelManager.getModelPrimaryKeyField(modelName);
        String pk = pkField.getFieldName();
        if (FieldType.LONG.equals(pkField.getFieldType())) {
            rows.forEach(row -> row.put(pk, convertIdToLong(row.get(pk))));
        } else if (FieldType.STRING.equals(pkField.getFieldType())) {
            rows.forEach(row -> row.put(pk, convertIdToString(row.get(pk))));
        }
    }

    /**
     * Determine if the id is a valid value.
     *
     * @param id id value
     * @return boolean
     */
    public static boolean validId(Object id) {
        return id != null && !id.equals(0) && !id.equals(0L) && !id.equals("");
    }

}
