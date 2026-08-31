package io.softa.framework.orm.jdbc;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

import io.softa.framework.base.utils.StringTools;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

/**
 * JDBCTemplate Map structure row data encapsulation.
 * To convert the underlined-separated database column key to camel case field name. e.g. dept_id -> deptId.
 */
public class ModelRowMapper implements RowMapper<Map<String, Object>> {

    private final String modelName;

    public ModelRowMapper(String modelName) {
        this.modelName = modelName;
    }

    /**
     * @param rs the ResultSet to map (pre-initialized for the current row)
     * @param rowNum the number of the current row
     * @return LinkedHashMap
     */
    @Override
    public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        Map<String, Object> resultMap = new LinkedHashMap<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String columnName = JdbcUtils.lookupColumnName(metaData, i);
            // Convert the column name to field name
            String fieldName;
            if (ModelConstant.SYSTEM_MODEL.contains(modelName)) {
                fieldName = StringTools.toCamelCase(columnName);
            } else {
                Optional<MetaField> field = ModelManager.getFieldByColumnName(modelName, columnName);
                if (field.isPresent()) {
                    fieldName = field.get().getFieldName();
                } else {
                    fieldName = columnName;
                }
            }
            resultMap.put(fieldName, normalizeTemporal(JdbcUtils.getResultSetValue(rs, i)));
        }
        return resultMap;
    }

    /**
     * Normalize the legacy {@code java.sql} temporal types to their {@code java.time} counterparts,
     * which is what the entity fields declare.
     *
     * <p><b>Why this is needed at all.</b> The two drivers disagree about what an untyped
     * {@code getObject} returns for a datetime column:
     *
     * <pre>
     *   column type          mysql-connector-j 9.x     pgjdbc 42.x
     *   DATETIME / TIMESTAMP java.time.LocalDateTime   java.sql.Timestamp
     * </pre>
     *
     * pgjdbc is the one following the JDBC spec here — {@code TIMESTAMP -> java.sql.Timestamp} is
     * the mapping the spec has defined since long before {@code java.time} existed. MySQL's driver
     * hands back the modern type as a convenience, which is why the gap below stayed invisible
     * while MySQL was the only backend: {@code java.sql.Timestamp} is <b>not</b> a subclass of
     * {@code java.sql.Date} (they are siblings under {@code java.util.Date}), so it fell straight
     * through the pre-existing Date branch and reached {@code BeanTool} unconverted, where
     * assigning it to a {@code LocalDateTime} field throws {@code TypeMismatchException}.
     *
     * <p>Every entity read via {@code selectMetaEntityList} inherits {@code createdTime} /
     * {@code updatedTime} from the audit base class, so on PostgreSQL this failed for every such
     * read — but only once the table had rows, since an empty result maps nothing at all. That is
     * why a fresh bootstrap looked healthy and only a database carrying data exposed it.
     *
     * <p><b>Conversion must not go through {@code Instant}.</b> These columns are
     * {@code TIMESTAMP WITHOUT TIME ZONE} — there is no zone to apply, and {@code toInstant()}
     * would impose the JVM's offset, silently shifting every stored value. {@code toLocalDateTime()}
     * / {@code toLocalTime()} / {@code toLocalDate()} copy the calendar fields across verbatim.
     */
    private static Object normalizeTemporal(Object value) {
        // Timestamp first: it extends java.util.Date, so an `instanceof Date` test placed above
        // would not catch it (they are siblings), but ordering still matters for readability.
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value;
    }
}
