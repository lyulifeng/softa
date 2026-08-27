package io.softa.starter.metadata.ddl.introspect;

import java.util.Locale;
import java.util.Set;

/**
 * Matches physical index names against declared ones — the one identifier-compat rule shared
 * by the drift audit ({@code PhysicalDriftAuditor}) and the convergence planner
 * ({@code DdlOrchestrator}), so what the audit reports and what the planner acts on can never
 * disagree.
 *
 * <p>Two engine quirks are absorbed here:
 * <ul>
 *   <li><b>Primary-key backing indexes</b> carry engine names, not declarations
 *       (MySQL {@code PRIMARY}, PostgreSQL {@code <table>_pkey}, H2 {@code PRIMARY_KEY_*}) —
 *       never "undeclared", never droppable.</li>
 *   <li><b>H2's synthetic unique-index suffix</b>: H2 (both compatibility modes) may report a
 *       unique index created as {@code uk_x} under {@code uk_x_INDEX_<n>}. Treating that as
 *       "declared {@code uk_x} missing + undeclared {@code uk_x_index_n} present" would make a
 *       converging boot drop and re-add the same index forever. MySQL / PostgreSQL keep names
 *       verbatim, so the suffix tolerance is inert on real deployments.</li>
 * </ul>
 */
public final class IndexNameCompat {

    private IndexNameCompat() {
    }

    /** Primary-key backing indexes carry engine names, not declarations. */
    public static boolean isPrimaryKeyIndex(String indexNameLower) {
        return indexNameLower.equals("primary")
                || indexNameLower.startsWith("primary_key")
                || indexNameLower.endsWith("_pkey");
    }

    /** Whether the declared index physically exists on the table — exact or engine-mangled name. */
    public static boolean declaredIndexExists(PhysicalSchema.PhysicalTable table, String declaredName) {
        String declaredLower = lower(declaredName);
        for (String physical : table.indexNames()) {
            if (physical.equals(declaredLower) || isSyntheticVariant(physical, declaredLower)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a physical index name matches any declared name — exact or engine-mangled. */
    public static boolean matchesAnyDeclared(String physicalNameLower, Set<String> declaredNamesLower) {
        if (declaredNamesLower.contains(physicalNameLower)) {
            return true;
        }
        for (String declared : declaredNamesLower) {
            if (isSyntheticVariant(physicalNameLower, declared)) {
                return true;
            }
        }
        return false;
    }

    /** H2's synthetic suffix: {@code <declared>_index_<n>} (lower-cased comparison). */
    private static boolean isSyntheticVariant(String physicalLower, String declaredLower) {
        if (!physicalLower.startsWith(declaredLower + "_index_")) {
            return false;
        }
        String suffix = physicalLower.substring(declaredLower.length() + "_index_".length());
        return !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit);
    }

    private static String lower(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }
}
