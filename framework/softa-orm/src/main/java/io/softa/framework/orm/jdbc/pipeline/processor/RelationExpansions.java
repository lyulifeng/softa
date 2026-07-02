package io.softa.framework.orm.jdbc.pipeline.processor;

import java.util.function.Supplier;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;

/**
 * Wrap cross-model reads triggered by the JDBC pipeline (XToOne / OneToMany
 * / ManyToMany display-name and reference expansion) so they bypass the
 * row-scope filter for the related model.
 *
 * <p>Rationale: when a user's query on {@code Employee} needs to render
 * {@code employee.department.name} (a manyToOne expansion), the JDBC
 * pipeline issues a follow-up {@code Department searchList} keyed by the
 * ids the outer query returned. That follow-up runs under the same
 * authenticated context; if the user has no Department scope grant, the
 * row-scope filter fail-closes to NEVER and the parent row's
 * {@code department} field silently collapses to null / unresolved id.
 *
 * <p>Bypassing row-scope for these expansions is safe because:
 * <ul>
 *   <li>The related-id set is derived from the outer query, which already
 *       cleared the row-scope filter for the primary model — the user is
 *       only ever asked to render ids their outer query legitimately
 *       returned.</li>
 *   <li>Standalone endpoints on the related model
 *       ({@code POST /Department/searchList}) never go through this
 *       helper; they enforce the full row-scope check as normal.</li>
 * </ul>
 *
 * <p>Package-private on purpose — this bypass is a JDBC-pipeline internal
 * concern, not a public API.
 */
final class RelationExpansions {

    private RelationExpansions() {}

    /**
     * Run {@code body} with {@code Context.skipPermissionCheck=true} for
     * the duration of the call, restoring the previous flag afterwards.
     *
     * <p>Falls through unchanged (no flag mutation) when no
     * {@link ContextHolder} scope is bound — the mutation would land on a
     * transient default {@link Context} and be discarded; the caller ends
     * up running the query with whatever flag was in effect anyway, which
     * on unbound paths (framework boot / lifecycle) is the safe
     * {@code false}. Legitimate authenticated requests always run inside
     * a bound {@code ContextHolder.callWith(...)}, so this branch is only
     * hit on non-request flows that don't have a user identity to enforce
     * scope against in the first place.
     */
    static <T> T withoutRowScope(Supplier<T> body) {
        if (!ContextHolder.existContext()) {
            return body.get();
        }
        Context ctx = ContextHolder.getContext();
        boolean previous = ctx.isSkipPermissionCheck();
        ctx.setSkipPermissionCheck(true);
        try {
            return body.get();
        } finally {
            ctx.setSkipPermissionCheck(previous);
        }
    }
}
