package io.softa.framework.base.constant;

import io.softa.framework.base.enums.Language;

/**
 * Global base constant
 */
public interface BaseConstant {

    Language DEFAULT_LANGUAGE = Language.EN_US;

    /** Debug parameter in request parameter */
    String DEBUG = "debug";
    /** Debug request header */
    String X_DEBUG = "X-Debug";
    /** The default top n value */
    Integer DEFAULT_TOP_N = 50;
    Integer DEFAULT_PAGE_NUMBER = 1;
    Integer DEFAULT_PAGE_SIZE = 50;
    Integer DEFAULT_BATCH_SIZE = 1000;
    Integer MAX_BATCH_SIZE = 10000;
    Integer MAX_EXPORT_SIZE = 100000;
    Integer DEFAULT_NAME_LIST_SIZE = 10;

    /** The default file size limit: 20MB */
    Integer DEFAULT_FILE_SIZE_LIMIT = 20 * 1024 * 1024;

    /** Cascading level restriction for cascade fields, for performance consideration, that is f0.f1.f2.f3.f4 */
    Integer CASCADE_LEVEL = 4;

    /** Max onDelete=CASCADE chain length (in models) allowed at boot; a longer chain is rejected by
     *  {@code ModelManager.validateCascadeAcyclic} — delete deep hierarchies explicitly in app code. */
    Integer MAX_CASCADE_DEPTH = 4;

    Integer DEFAULT_SCALE = 2;

    /** The optionSet code of Boolean field */
    String BOOLEAN_OPTION_SET_CODE = "BooleanValue";

    /** The directory of predefined data, located in src/resources/data-system/ */
    String PREDEFINED_DATA_SYSTEM_DIR = "data-system/";
    /** The directory of predefined tenant data, located in src/resources/data-tenant/ */
    String PREDEFINED_DATA_TENANT_DIR = "data-tenant/";
    /** The directory of predefined platform-tier data, located in src/resources/data-platform/ */
    String PREDEFINED_DATA_PLATFORM_DIR = "data-platform/";

    /**
     * The platform tier's tenant id on {@code multiTenant} models: rows owned by
     * the platform operator, not by any tenant. Deliberately {@code -1} — never
     * {@code 0}, which collides with "no tenant selected" defaults (unset
     * context fallbacks), and never a real tenant id (id generation is
     * positive). Framework-wide constant: every starter that keeps a platform
     * tier (message templates, server configs, provider routing, quotas)
     * compares against this value.
     */
    Long PLATFORM_TENANT_ID = -1L;

    String SESSION_ID = "sessionId";
    String SESSION_ID_HEADER = "X-Session-Id";

    String TOKEN = "token";
    String AUTHORIZATION = "Authorization";

    // TraceId in the request header
    String X_B3_TRACEID = "X-B3-TraceId";

    /** Carries the company selected in the UI header; read into Context per request. */
    String COMPANY_ID_HEADER = "X-Company-Id";

    /**
     * Names the country to narrow by on a request that deliberately sends no company — a screen that
     * must reach across the caller's companies while still belonging to one country.
     *
     * <p>Ignored whenever {@link #COMPANY_ID_HEADER} is present: there the country is resolved from
     * that company server-side, so the two can never disagree.
     */
    String COMPANY_COUNTRY_HEADER = "X-Company-Country";

}
