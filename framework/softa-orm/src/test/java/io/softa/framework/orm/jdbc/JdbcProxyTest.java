package io.softa.framework.orm.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.jdbc.database.SqlParams;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JdbcProxy#insert} must name the generated-key column instead of asking for
 * {@code Statement.RETURN_GENERATED_KEYS}.
 *
 * <p>pgjdbc rewrites that flag into {@code RETURNING *}, so the key holder comes back
 * carrying every column of the inserted row and {@code KeyHolder.getKey()} throws
 * {@code InvalidDataAccessApiUsageException} ("multiple keys"). Naming the column keeps the
 * key set single-entry on both flavors.
 *
 * <p>The timeline case has no runtime coverage otherwise: the only timeline model in the
 * repo today ({@code DeptInfo}) is {@code DISTRIBUTED_LONG}, so it never enters this method.
 * The first timeline + {@code DB_AUTO_ID} model would be the first to exercise the
 * {@code slice_id} branch — this test stands in for it until then.
 */
class JdbcProxyTest {

    private static final String SQL = "INSERT INTO t (a) VALUES (?)";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcProxy jdbcProxy = new JdbcProxy();

    private Connection connection;

    @BeforeEach
    void injectTemplate() {
        ReflectionTestUtils.setField(jdbcProxy, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void namesTheIdColumnForAnOrdinaryModel() throws SQLException {
        assertArrayEquals(new String[]{"id"}, generatedKeyColumns("EmpInfo", "id"));
    }

    @Test
    void namesTheSliceIdColumnForATimelineModel() throws SQLException {
        // A timeline model's physical primary key is slice_id, not id.
        assertArrayEquals(new String[]{"slice_id"}, generatedKeyColumns("PriceTimeline", "slice_id"));
    }

    @Test
    void neverRequestsReturnGeneratedKeys() throws SQLException {
        generatedKeyColumns("EmpInfo", "id");
        // The int overload is what pgjdbc turns into RETURNING * — it must not be reached.
        verify(connection, never()).prepareStatement(anyString(), anyInt());
    }

    @Test
    void returnsTheGeneratedKey() throws SQLException {
        assertEquals(7L, insertWith("EmpInfo", "id"));
    }

    /** Runs an insert and returns the column-name array handed to {@code prepareStatement}. */
    private String[] generatedKeyColumns(String modelName, String pkColumn) throws SQLException {
        insertWith(modelName, pkColumn);
        ArgumentCaptor<String[]> columns = ArgumentCaptor.forClass(String[].class);
        verify(connection).prepareStatement(anyString(), columns.capture());
        return columns.getValue();
    }

    /**
     * Drive {@code insert} with a stubbed template that behaves like Spring's: it runs the
     * supplied creator against a mock connection, then drops a single-entry key row into the
     * holder the proxy created.
     */
    private Long insertWith(String modelName, String pkColumn) throws SQLException {
        connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(statement);

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    PreparedStatementCreator creator = invocation.getArgument(0);
                    creator.createPreparedStatement(connection);
                    KeyHolder keyHolder = invocation.getArgument(1);
                    Map<String, Object> key = new LinkedHashMap<>();
                    key.put(pkColumn, 7L);
                    keyHolder.getKeyList().add(key);
                    return 1;
                });

        MetaField primaryKey = mock(MetaField.class);
        when(primaryKey.getColumnName()).thenReturn(pkColumn);

        SqlParams sqlParams = new SqlParams(SQL);
        sqlParams.addArgValue("a-value");

        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            modelManager.when(() -> ModelManager.getModelPrimaryKeyField(modelName)).thenReturn(primaryKey);
            return jdbcProxy.insert(modelName, sqlParams);
        }
    }
}
