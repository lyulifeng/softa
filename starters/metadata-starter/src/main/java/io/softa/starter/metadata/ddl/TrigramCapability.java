package io.softa.starter.metadata.ddl;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import io.softa.framework.orm.enums.DatabaseType;

/**
 * Whether this database can actually build a trigram index right now.
 *
 * <p>A SEARCH-method index renders as {@code USING gin (col gin_trgm_ops)} on PostgreSQL, which
 * needs the {@code pg_trgm} extension. The check runs in two steps: first ask whether the
 * operator class is already usable, and if not, try {@code CREATE EXTENSION IF NOT EXISTS
 * pg_trgm} once — the extension is <i>trusted</i> since PostgreSQL 13, so the application's
 * database-owner role can usually create it without superuser rights (managed platforms
 * included). Only when both fail does the planner treat trigram indexes as unbuildable.
 *
 * <p><b>The probe deliberately does not ask {@code pg_extension}.</b> That view answers "is the
 * extension installed <i>somewhere</i>", but {@code gin_trgm_ops} only resolves if the schema it
 * was installed into is on the connection's {@code search_path}. A deployment that keeps
 * extensions in their own schema — a common hygiene rule on managed platforms — would pass an
 * {@code extname = 'pg_trgm'} check and then still fail with "operator class does not exist",
 * which is the exact error this check exists to prevent. Asking whether the opclass is
 * <i>visible</i> tests the thing the DDL actually needs.
 *
 * <p>When unavailable, the planner <b>skips</b> the affected indexes rather than failing the
 * boot: these rows are mostly derived from {@code searchName} on models nobody annotated for
 * this purpose, and a missing optimization must never take the application down. The rows stay
 * in {@code sys_model_index}, so the first boot after a DBA installs the extension creates them
 * with no code change. Skipping must never mean "fall back to a B-tree": the physical snapshot
 * compares index <i>names</i> only, so a B-tree wearing a {@code _search} name would look
 * converged forever and the search would silently stay a sequential scan.
 *
 * <p>Off PostgreSQL there is nothing to provision and nothing to skip — the other dialects
 * render a SEARCH index as a plain one (their engines have no substring index to offer), so
 * {@link #available()} is simply {@code true} there and the planner leaves the context alone.
 */
@Slf4j
final class TrigramCapability {

    /**
     * True when {@code gin_trgm_ops} is resolvable on the current {@code search_path}.
     * {@code pg_opclass_is_visible} is what makes this a visibility test rather than an
     * installed-anywhere test.
     */
    private static final String PROBE = """
            SELECT 1 FROM pg_opclass o
              JOIN pg_am a ON a.oid = o.opcmethod
             WHERE a.amname = 'gin'
               AND o.opcname = 'gin_trgm_ops'
               AND pg_opclass_is_visible(o.oid)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseType databaseType;

    /** Resolved at most once per orchestrator: the answer cannot change inside one boot. */
    private Boolean available;

    TrigramCapability(JdbcTemplate jdbcTemplate, DatabaseType databaseType) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseType = databaseType;
    }

    /** @return true when a SEARCH index can be built as rendered by the current dialect. */
    boolean available() {
        if (databaseType != DatabaseType.POSTGRESQL) {
            return true;
        }
        if (available == null) {
            available = probeOrProvision();
        }
        return available;
    }

    /**
     * Any failure reads as "not available". A role locked down enough to be refused
     * {@code pg_catalog} reads must not fail the boot of an application that may not even use a
     * trigram index — and on H2 (which the DDL tests run against in PostgreSQL compatibility
     * mode) neither statement resolves, which is the answer those tests want anyway.
     */
    private boolean probeOrProvision() {
        if (probe()) {
            return true;
        }
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            log.info("TrigramCapability: created the pg_trgm extension (trusted since "
                    + "PostgreSQL 13) for SEARCH-method indexes");
        } catch (RuntimeException e) {
            log.debug("TrigramCapability: CREATE EXTENSION pg_trgm failed, probing visibility "
                    + "once more before treating trigram indexes as unbuildable", e);
        }
        return probe();
    }

    private boolean probe() {
        try {
            List<Integer> rows = jdbcTemplate.queryForList(PROBE, Integer.class);
            return !rows.isEmpty();
        } catch (RuntimeException e) {
            log.debug("TrigramCapability: pg_trgm probe failed, treating as unavailable", e);
            return false;
        }
    }

    /** Set on the first skip so the actionable remediation is logged once, not per model. */
    private boolean warnedSkip;

    /**
     * Report indexes the planner dropped because of this capability. Lives here rather than in
     * the planner so every PostgreSQL-specific word — extension name, {@code search_path},
     * the operator class, the remediation — stays in the one class that owns that knowledge;
     * the planner only knows "a SEARCH index may be unbuildable".
     */
    void warnSkipped(List<String> indexNames, String tableName) {
        if (warnedSkip) {
            log.debug("TrigramCapability: skipping SEARCH index(es) {} on {} — pg_trgm unavailable",
                    indexNames, tableName);
            return;
        }
        warnedSkip = true;
        log.warn("TrigramCapability: skipping SEARCH index(es) {} on {} — the pg_trgm extension is "
                        + "not available on the current search_path and this role may not create "
                        + "it, so `USING gin (... gin_trgm_ops)` would fail. Ask a DBA to run: "
                        + "CREATE EXTENSION pg_trgm; the indexes are created on the next boot, "
                        + "no code change needed.",
                indexNames, tableName);
    }
}
