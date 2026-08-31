package io.softa.starter.metadata.ddl;

import java.sql.SQLException;

import org.springframework.jdbc.BadSqlGrammarException;

/**
 * Classifies SQL exceptions from DDL execution into "idempotent duplicate"
 * (the DDL has already been applied) vs genuine failures.
 *
 * <p>Extracted from {@code DdlOrchestrator} so both the scanner-time path
 * (boot reconciliation) and the deploy-time path (runtime DDL execution
 * triggered by {@link DdlExecutor}) classify
 * errors the same way.
 *
 * <p>Idempotent-duplicate error codes recognised:
 * <ul>
 *   <li>MySQL 1050 (table exists), 1060 (column exists), 1061 (duplicate key name)</li>
 *   <li>PostgreSQL 42P07 (duplicate_table), 42701 (duplicate_column), 42P11 (duplicate_index)</li>
 *   <li>H2 42101 (table exists), 42121 (duplicate column), 42111 (index exists),
 *       90045 (constraint exists) — H2 backs the test suite and lightweight dev runs;
 *       its vendor codes cannot collide with MySQL's (&lt; 10000) and PostgreSQL signals
 *       via SQLState.
 *       <p>90045 is here because H2 answers {@code ALTER TABLE … ADD UNIQUE INDEX} with a
 *       <em>constraint</em>, so a re-run reports a duplicate constraint rather than a duplicate
 *       index — a different code for the same "already applied" state MySQL reports as 1061.
 *       Without it, re-running unique-index DDL against H2 is a hard failure while the same
 *       DDL degrades cleanly on MySQL, which is the one place the two must not disagree.</li>
 * </ul>
 *
 * <p><b>Rename re-runs</b>: a declared rename runs its DDL before the
 * {@code sys_*} write commits, so a boot that applies the {@code CHANGE COLUMN} /
 * {@code RENAME TABLE} but fails the row write retries the same rename next boot —
 * now against the already-renamed schema, where the <em>old</em> name is gone. The
 * narrow predicates below recognise that "source already renamed / already dropped"
 * state so the retry degrades to WARN and converges. They are kept separate from
 * {@link #isIdempotentDuplicate} and consulted only for the matching
 * {@code RenderedDdl.Kind}, so a genuine "unknown column" / "missing table" on an
 * ordinary ALTER still surfaces as a hard failure.
 */
public final class DdlErrorClassifier {

    private DdlErrorClassifier() {}

    public static boolean isIdempotentDuplicate(BadSqlGrammarException e) {
        SQLException sqle = e.getSQLException();
        if (sqle == null) {
            return false;
        }
        int code = sqle.getErrorCode();
        if (code == 1050 || code == 1060 || code == 1061) {
            return true;
        }
        if (code == 42101 || code == 42121 || code == 42111 || code == 90045) {
            return true;
        }
        String state = sqle.getSQLState();
        return "42P07".equals(state) || "42701".equals(state) || "42P11".equals(state);
    }

    /**
     * A {@code CHANGE COLUMN old new ...} (declared field rename) re-run where
     * {@code old} no longer exists — MySQL 1054 (unknown column), PostgreSQL 42703
     * (undefined_column), H2 42122 (column not found). Consult only for
     * {@code DECLARED_COLUMN_RENAME}.
     */
    public static boolean isColumnRenameAlreadyApplied(BadSqlGrammarException e) {
        SQLException sqle = e.getSQLException();
        if (sqle == null) {
            return false;
        }
        int code = sqle.getErrorCode();
        return code == 1054 || code == 42122 || "42703".equals(sqle.getSQLState());
    }

    /**
     * A {@code RENAME TABLE old TO new} (declared model rename) re-run where
     * {@code old} no longer exists — MySQL 1146 (no such table), PostgreSQL 42P01
     * (undefined_table), H2 42102 (table not found). Consult only for
     * {@code DECLARED_TABLE_RENAME}.
     */
    public static boolean isTableRenameAlreadyApplied(BadSqlGrammarException e) {
        SQLException sqle = e.getSQLException();
        if (sqle == null) {
            return false;
        }
        int code = sqle.getErrorCode();
        return code == 1146 || code == 42102 || "42P01".equals(sqle.getSQLState());
    }

    /**
     * A {@code DROP COLUMN} whose column no longer exists — MySQL 1091 (can't drop:
     * check that it exists), PostgreSQL 42703 (undefined_column), H2 42122 (column not
     * found). Consult only for {@code DROP_COLUMN}: the convergence lane's undeclared-column
     * cleanup plans from a snapshot, so a column hand-dropped between snapshot and execution
     * is already in the target state and the boot must converge, not fail.
     */
    public static boolean isColumnDropAlreadyApplied(BadSqlGrammarException e) {
        SQLException sqle = e.getSQLException();
        if (sqle == null) {
            return false;
        }
        int code = sqle.getErrorCode();
        return code == 1091 || code == 42122 || "42703".equals(sqle.getSQLState());
    }

    /**
     * A {@code DROP INDEX} whose index no longer exists — MySQL 1091 (can't drop:
     * check that it exists), PostgreSQL 42704 (undefined_object), H2 42112 (index
     * not found) / 90057 (constraint not found — H2's MySQL mode routes
     * {@code ALTER TABLE ... DROP INDEX} through constraint lookup). Consulted for
     * {@code ALTER_INDEX} — the drop-half of an index rebuild (definition change),
     * where a missing index means the drop is already done and the following ADD must
     * still run — and for the convergence lane's undeclared-index {@code DROP_INDEX}
     * units, whose snapshot may be stale the same way.
     */
    public static boolean isIndexDropAlreadyApplied(BadSqlGrammarException e) {
        SQLException sqle = e.getSQLException();
        if (sqle == null) {
            return false;
        }
        int code = sqle.getErrorCode();
        return code == 1091 || code == 42112 || code == 90057 || "42704".equals(sqle.getSQLState());
    }

    /** Walk the cause chain to the root and return its message. */
    public static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }
}
