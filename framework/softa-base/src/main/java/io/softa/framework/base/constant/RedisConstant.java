package io.softa.framework.base.constant;

public interface RedisConstant {
    /** Expiration time in seconds */
    int ONE_MINUTES = 60;
    int FIVE_MINUTES = 60 * 5;
    int ONE_HOUR = 60 * 60;
    int ONE_DAY = 60 * 60 * 24;
    int ONE_WEEK = 60 * 60 * 24 * 7;
    int ONE_MONTH = 60 * 60 * 24 * 30;
    int ONE_QUARTER = 60 * 60 * 24 * 90;
    int ONE_YEAR = 60 * 60 * 24 * 365;

    // The default expiration time of the cache is ONE_DAY.
    int DEFAULT_EXPIRE_SECONDS = ONE_MONTH;

    /** redis key routes */
    String SESSION =  "session:";
    String USER_INFO =  "user-info:";
    String EMP_INFO = "emp-info:";
    /**
     * Company → its country. Caches the stable mapping, never the per-request selection:
     * caching which company is selected would defeat the header switcher, and two browser
     * tabs would overwrite each other.
     *
     * <p>"Company" is the framework's word for what the HR app calls a legal entity — the same
     * translation {@code EmpInfo.companyId} and {@code USER_COMP_ID} already make.
     */
    String COMPANY_COUNTRY = "company-country:";
    String PERMISSION_INFO =  "permission-info:";
    String VERIFICATION_CODE =  "verification-code:";
    String TENANT_IDS =  "tenant:id-list";
    String TENANT_INFO =  "tenant-info:";
    /** Per-tenant entitlement (resolved module set); full key: {@code entl:{tenantId}}. */
    String ENTITLEMENT = "entl:";

    String TEMP_TOKEN = "temp-token:";
    String ONE_TIME_KEY = "one-time-key:";

    /** Sequence configuration cache key prefix; full key: {rootKey}:seq-config:{tenantId}:{code} */
    String SEQUENCE_CONFIG = "seq-config:";
}
