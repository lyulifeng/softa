package io.softa.framework.orm.jdbc.database;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.utils.StringTools;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

/**
 * Static SQL Builder
 */
public class StaticSqlBuilder {

    private StaticSqlBuilder() {}

    /**
     * Get the table name by model name.
     *
     * @param modelName model name
     * @return table name
     */
    private static String getTableName(String modelName) {
        return ModelManager.getModel(modelName).getTableName();
    }

    /**
     * Build INSERT SQL based on model name and the Map data:
     *      INSERT INTO table_name (f1,f2,f3) VALUES (?,?,?)
     *
     * @param modelName model name
     * @param fields field list
     * @return INSERT SQL
     */
    public static SqlParams getInsertSql(String modelName, List<String> fields) {
        StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(getTableName(modelName)).append(" (");
        StringBuilder placeholder = new StringBuilder();
        for (String field : fields) {
            MetaField metaField = ModelManager.getModelField(modelName, field);
            insertSql.append(metaField.getColumnName()).append(",");
            placeholder.append("?,");
        }
        // Remove the last character.
        insertSql.deleteCharAt(insertSql.length() - 1);
        placeholder.deleteCharAt(placeholder.length() - 1);
        // Add the placeholder of field value
        insertSql.append(") VALUES (").append(placeholder).append(")");
        // SQL params
        return new SqlParams(insertSql.toString());
    }

    /**
     * Build SELECT SQL based on model name, field list and ids:
     *      SELECT f1,f2,f3 FROM table_name WHERE id=?
     *
     * @param modelName model name
     * @param fields field list
     * @param ids id list
     * @return SELECT SQL
     */
    public static <K extends Serializable> SqlParams getSelectSql(String modelName, List<String> fields, List<K> ids) {
        return getSelectSql(modelName, fields, ids, ModelConstant.ID);
    }

    /**
     * SELECT by an arbitrary key field, rendered as its COLUMN name:
     *      SELECT f1,f2 FROM table_name WHERE key_column IN (?,?)
     * Used for pk-keyed reads where the pk differs from the logical id
     * (a timeline model's {@code sliceId} → {@code slice_id}).
     *
     * @param modelName model name
     * @param fields field list
     * @param ids key value list
     * @param keyField the field the WHERE clause keys on
     * @return SELECT SQL
     */
    public static <K extends Serializable> SqlParams getSelectSql(String modelName, List<String> fields,
                                                                  List<K> ids, String keyField) {
        SqlParams sqlParams = new SqlParams();
        StringBuilder readSql = new StringBuilder("SELECT ");
        fields.forEach(field -> readSql.append(ModelManager.getModelField(modelName, field).getColumnName()).append(","));
        // Remove the last character.
        readSql.deleteCharAt(readSql.length() - 1);
        readSql.append(" FROM ").append(getTableName(modelName));
        appendKeySql(readSql, ModelManager.getModelField(modelName, keyField).getColumnName(), ids.size());
        // Add the key list to the SQL parameter list.
        sqlParams.setArgs(new ArrayList<>(ids));
        if (ModelManager.isMultiTenantControl(modelName)) {
            // Add tenantId condition
            readSql.append(" AND tenant_id = ? ");
            sqlParams.addArgValue(ContextHolder.getContext().getTenantId());
        }
        sqlParams.setSql(readSql.toString());
        return sqlParams;
    }

    /**
     * SELECT all metadata, without permission and multi-tenancy filters.
     *      SELECT * FROM table_name WHERE id=?
     *
     * @param modelName model name
     * @param orderBy order by field
     * @return SELECT SQL
     */
    public static SqlParams getSelectAllMetaSql(String modelName, String orderBy) {
        // The table name metadata must be in underscore case.
        String tableName = StringTools.toUnderscoreCase(modelName);
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
        if (StringUtils.isNotBlank(orderBy) && StringTools.isTableOrColumn(orderBy)) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        return new SqlParams(sql.toString());
    }

    /**
     * Build UPDATE SQL based on model and data:
     *      UPDATE table_name SET f1=?,f2=?,f3=? WHERE id = ?
     *
     * @param modelName model name
     * @param rowMap data row
     * @return UPDATE SQL
     */
    public static SqlParams getUpdateSql(String modelName, Map<String, Object> rowMap) {
        StringBuilder updateSql = new StringBuilder("UPDATE ").append(getTableName(modelName)).append(" SET ");
        // Append `SET` fields
        String pk = ModelManager.getModelPrimaryKey(modelName);
        List<String> fields = new ArrayList<>(rowMap.keySet());
        fields.remove(pk);
        fields.forEach(field -> updateSql.append(ModelManager.getModelField(modelName, field).getColumnName()).append("=?,"));
        // Remove the last character
        updateSql.deleteCharAt(updateSql.length() - 1);
        // Append the pk condition to the WHERE clause — as a COLUMN name, like the SET side
        // (the pk FIELD name `sliceId` differs from its column `slice_id` on timeline models).
        String pkColumnName = ModelManager.getModelField(modelName, pk).getColumnName();
        updateSql.append(" WHERE ").append(pkColumnName).append(" = ?");
        // SQL params object
        SqlParams sqlParams = new SqlParams(updateSql.toString());
        fields.forEach(field -> sqlParams.addArgValue(rowMap.get(field)));
        // Add the pk value to the end of `valueArray`
        sqlParams.addArgValue(rowMap.get(pk));
        return sqlParams;
    }

    /**
     * Build DELETE SQL based on model name and ids:
     *      Physical delete : DELETE FROM table_name WHERE id = ?
     *      Soft delete: UPDATE table_name SET deleted=true WHERE id = ?
     *
     * @param modelName model name
     * @param ids id list
     * @return DELETE SQL
     */
    public static SqlParams getDeleteSql(String modelName, List<?> ids) {
        StringBuilder deleteSql = new StringBuilder("DELETE FROM ").append(getTableName(modelName));
        appendKeySql(deleteSql, ModelConstant.ID, ids.size());
        // SQL params
        SqlParams sqlParams = new SqlParams(deleteSql.toString());
        sqlParams.setArgs(new ArrayList<>(ids));
        return sqlParams;
    }

    /**
     * Build DELETE SQL for timeline model based on modelName and sliceId:
     *      DELETE FROM table_name WHERE sliceId = ?
     *
     * @param modelName model name
     * @param sliceId slice id of timeline model
     * @return DELETE SQL for timeline model
     */
    public static SqlParams getDeleteTimelineSliceSql(String modelName, Serializable sliceId) {
        StringBuilder deleteSql = new StringBuilder("DELETE FROM ")
                .append(getTableName(modelName))
                .append(" WHERE ")
                .append(ModelConstant.SLICE_ID_COLUMN)
                .append(" = ?");
        // SQL params
        SqlParams sqlParams = new SqlParams(deleteSql.toString());
        sqlParams.addArgValue(sliceId);
        return sqlParams;
    }

    /**
     * Append the WHERE clause keyed on the given COLUMN: `WHERE col = ?` / `WHERE col IN (?,?)`.
     *
     * @param sqlBuilder sb
     * @param keyColumn key column name
     * @param idsSize ids size
     */
    private static void appendKeySql(StringBuilder sqlBuilder, String keyColumn, int idsSize) {
        if (idsSize > 1) {
            sqlBuilder.append(" WHERE ").append(keyColumn).append(" IN (");
            IntStream.range(0, idsSize).forEach(i -> sqlBuilder.append("?,"));
            sqlBuilder.deleteCharAt(sqlBuilder.length() - 1);
            sqlBuilder.append(")");
        } else {
            sqlBuilder.append(" WHERE ").append(keyColumn).append(" = ?");
        }
    }

}
