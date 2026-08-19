package io.softa.starter.metadata.ddl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.orm.enums.DatabaseType;
import io.softa.framework.orm.jdbc.database.DBUtil;

/**
 * Cross-instance mutual exclusion for the boot-time DDL window (catalog physical
 * reconcile → strict read → diff → DDL → {@code sys_*} row writes). Without it, N
 * replicas booting the same fresh or drifted database race their DDL: the losers
 * mostly degrade via the already-applied classification, but interleaved
 * CREATE/ALTER between two planners is a correctness gamble this makes unnecessary.
 *
 * <p>Database-native session locks — MySQL {@code GET_LOCK} / PostgreSQL
 * {@code pg_advisory_lock} — on a <b>dedicated connection held for the lock's whole
 * lifetime</b>: both engines scope the lock to the session, so it must never be
 * acquired through a pooled connection that the pool could hand to somebody else.
 * The application's own statements keep flowing through the normal pool. If the
 * process dies mid-boot the session dies with it and the engine releases the lock —
 * no lease bookkeeping.
 *
 * <p>Wait budget: {@link #TIMEOUT_SECONDS}. A well-behaved sibling finishes its DDL
 * window in seconds, so a timeout means something is stuck — boot fails with a
 * pointer rather than piling instances behind a wedged lock (the supervisor's
 * restart is the retry).
 *
 * <p>Unsupported dialects and test seams (no {@link DataSource}) return {@code null}
 * — the caller proceeds lockless, which is exactly the pre-lock behavior.
 */
@Slf4j
public final class BootDdlLock implements AutoCloseable {

    static final int TIMEOUT_SECONDS = 60;
    private static final long PG_POLL_MILLIS = 500;

    private final Connection connection;
    private final DatabaseType type;
    private final String name;

    private BootDdlLock(Connection connection, DatabaseType type, String name) {
        this.connection = connection;
        this.type = type;
        this.name = name;
    }

    /**
     * Acquire the boot-DDL lock, waiting up to {@link #TIMEOUT_SECONDS}.
     *
     * @return the held lock, or {@code null} when locking is not applicable (no
     *         datasource, unparseable URL, or a dialect without session locks)
     * @throws IllegalStateException on wait timeout or a lock-acquisition failure
     */
    public static BootDdlLock acquire(DataSource dataSource, String datasourceUrl) {
        if (dataSource == null) {
            return null;
        }
        DatabaseType type;
        try {
            type = DBUtil.parseDatabaseType(datasourceUrl);
        } catch (RuntimeException e) {
            log.debug("BootDdlLock: datasource url not parseable ({}); proceeding lockless", e.getMessage());
            return null;
        }
        if (type != DatabaseType.MYSQL && type != DatabaseType.POSTGRESQL) {
            log.debug("BootDdlLock: no session-lock support for {}; proceeding lockless", type);
            return null;
        }
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            String name = "softa:boot-ddl:" + scopeName(connection);
            boolean acquired = switch (type) {
                case MYSQL -> acquireMysql(connection, name);
                case POSTGRESQL -> acquirePostgres(connection, name);
                default -> throw new IllegalStateException("unreachable");
            };
            if (!acquired) {
                throw new IllegalStateException("Timed out after " + TIMEOUT_SECONDS + "s waiting for the boot "
                        + "DDL lock '" + name + "' — another instance appears stuck in its boot DDL window. "
                        + "Check its logs, then restart this instance.");
            }
            log.info("BootDdlLock: acquired '{}'", name);
            BootDdlLock lock = new BootDdlLock(connection, type, name);
            connection = null;   // ownership transferred
            return lock;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire the boot DDL lock", e);
        } finally {
            closeQuietly(connection);
        }
    }

    private static boolean acquireMysql(Connection connection, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, name);
            ps.setInt(2, TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    /** {@code pg_advisory_lock} has no wait budget — poll the try-variant against a deadline. */
    private static boolean acquirePostgres(Connection connection, String name) throws SQLException {
        long deadline = System.nanoTime() + TIMEOUT_SECONDS * 1_000_000_000L;
        while (true) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) {
                        return true;
                    }
                }
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(PG_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the boot DDL lock", e);
            }
        }
    }

    /** Both drivers report the connected database through the catalog. */
    private static String scopeName(Connection connection) throws SQLException {
        String catalog = connection.getCatalog();
        return catalog == null || catalog.isBlank() ? "default" : catalog;
    }

    @Override
    public void close() {
        try (PreparedStatement ps = connection.prepareStatement(switch (type) {
            case MYSQL -> "SELECT RELEASE_LOCK(?)";
            case POSTGRESQL -> "SELECT pg_advisory_unlock(hashtext(?))";
            default -> throw new IllegalStateException("unreachable");
        })) {
            ps.setString(1, name);
            ps.executeQuery().close();
            log.info("BootDdlLock: released '{}'", name);
        } catch (SQLException e) {
            // The session's death releases the lock anyway — closing the connection below is
            // the real release; this explicit call just keeps the session reusable semantics tidy.
            log.warn("BootDdlLock: explicit release of '{}' failed ({}); closing the session releases it",
                    name, e.getMessage());
        } finally {
            closeQuietly(connection);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("BootDdlLock: connection close failed: {}", e.getMessage());
            }
        }
    }
}
