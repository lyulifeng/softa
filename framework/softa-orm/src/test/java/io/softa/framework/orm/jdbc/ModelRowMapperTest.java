package io.softa.framework.orm.jdbc;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ModelRowMapper} must normalize the legacy {@code java.sql} temporal types to
 * their {@code java.time} counterparts. mysql-connector-j already returns
 * {@code java.time} values from an untyped {@code getObject}, but pgjdbc follows the
 * JDBC spec and returns {@code Timestamp} / {@code Time} / {@code Date} — unconverted,
 * those fail bean assignment to the {@code LocalDateTime} / {@code LocalTime} /
 * {@code LocalDate} fields the entities declare.
 *
 * <p>The expected values assert calendar-field-preserving conversion (no
 * {@code Instant} round-trip): a zone-shifting conversion would move values near
 * midnight onto a different date and corrupt every stored timestamp.
 */
class ModelRowMapperTest {

    @Test
    void normalizesLegacySqlTemporalsToJavaTime() throws SQLException {
        LocalDateTime dateTime = LocalDateTime.of(2026, 3, 1, 23, 59, 58);

        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(4);
        stubColumn(metaData, 1, "created_time");
        stubColumn(metaData, 2, "start_time");
        stubColumn(metaData, 3, "effective_date");
        stubColumn(metaData, 4, "name");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(metaData);
        when(rs.getObject(1)).thenReturn(Timestamp.valueOf(dateTime));
        when(rs.getObject(2)).thenReturn(Time.valueOf(LocalTime.of(8, 30, 0)));
        when(rs.getObject(3)).thenReturn(Date.valueOf(LocalDate.of(2026, 3, 1)));
        when(rs.getObject(4)).thenReturn("plain");

        // A SYSTEM_MODEL name keeps column→field mapping on the static camel-case
        // path, so the test needs no ModelManager metadata.
        Map<String, Object> row = new ModelRowMapper("SysModel").mapRow(rs, 0);

        assertEquals(dateTime, row.get("createdTime"));
        assertEquals(LocalTime.of(8, 30, 0), row.get("startTime"));
        assertEquals(LocalDate.of(2026, 3, 1), row.get("effectiveDate"));
        assertEquals("plain", row.get("name"));
    }

    private static void stubColumn(ResultSetMetaData metaData, int index, String name)
            throws SQLException {
        when(metaData.getColumnLabel(index)).thenReturn(name);
        when(metaData.getColumnName(index)).thenReturn(name);
    }
}
