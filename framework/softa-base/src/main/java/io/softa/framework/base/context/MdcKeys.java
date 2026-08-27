package io.softa.framework.base.context;

/**
 * SLF4J MDC keys the framework maintains for the duration of a bound {@link Context}
 * scope (see softa-web's ContextScopeFilter). Log encoders and MDC-aware integrations
 * (structured logging, Sentry) emit these as per-line fields; consumers that need to
 * read them back should reference these constants instead of repeating the literals.
 */
public final class MdcKeys {

    public static final String TRACE_ID = "traceId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

    private MdcKeys() {}
}
