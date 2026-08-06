package io.softa.framework.web.filter.context;

import io.softa.framework.base.context.Context;

/**
 * SPI for enriching a {@link Context} after core identity (UserInfo) has been set.
 *
 * <p>Business starters (e.g., HR module, permission module) provide Spring beans
 * implementing this interface. {@link ContextBuilder} collects all enrichers via
 * dependency injection and invokes them during {@code buildUserContext()}.
 *
 * <p>Enrichers are called <em>after</em> {@code UserInfo}, {@code tenantId}, and
 * {@code language} are already populated on the context, so implementations may
 * rely on {@code context.getUserId()} and other base fields.
 *
 * <p>Typical implementations read cached data from Redis (via {@code CacheService})
 * and fall back to a database query on cache-miss, following the same pattern used
 * for {@code UserInfo}.
 *
 * <h3>Ordering</h3>
 * {@code ContextBuilder} invokes enrichers in Spring's {@code @Order} sequence, and an
 * implementation that reads what another one wrote <b>must</b> declare {@link #ORDER_DERIVED} —
 * beans with no order are collected in whatever sequence the container happened to register them,
 * which is stable enough per build to pass every test and reorder on an unrelated change. Only two
 * tiers are defined, because a third would mean two enrichers deriving from each other; that is a
 * single enricher.
 *
 * <p>Reading another enricher's output stays <em>optional</em> either way: the writer may be absent
 * (its starter is not on the classpath) or may degrade to a no-op, so the reader must treat the value
 * as possibly missing rather than assume ordering guarantees presence.
 */
@FunctionalInterface
public interface ContextEnricher {

    /** Reads only the base identity fields ({@code userId} / {@code tenantId} / {@code language}). */
    int ORDER_IDENTITY = 100;

    /** May additionally read what an {@link #ORDER_IDENTITY} enricher put on the context. */
    int ORDER_DERIVED = 200;

    /**
     * Enrich the given context with additional data.
     *
     * @param context the context to enrich (UserInfo is already set)
     */
    void enrich(Context context);
}

