package com.ibm.websphere.sample.util;

/**
 * Application-wide constants.
 *
 * WebSphere Best Practices captured here:
 *  - JNDI names follow WebSphere's java:comp/env namespace convention.
 *  - DataSource JNDI names are configured in the WebSphere Admin Console
 *    (Resources > JDBC > Data Sources) and bound under java:comp/env via
 *    resource-ref entries in web.xml / ejb-jar.xml.
 *  - JMS resources follow the same pattern.
 */
public final class AppConstants {

    private AppConstants() { /* utility class */ }

    // -------------------------------------------------------------------------
    // JNDI Names — configured in WebSphere Admin Console
    // -------------------------------------------------------------------------
    /** WebSphere DataSource JNDI name for PostgreSQL connection pool */
    public static final String DS_JNDI              = "java:comp/env/jdbc/PostgresDS";

    /** Global JNDI for JMS Connection Factory (SIBus / MQ) */
    public static final String JMS_CF_JNDI          = "java:comp/env/jms/EmployeeQCF";

    /** JMS Queue JNDI for async employee processing */
    public static final String JMS_QUEUE_JNDI       = "java:comp/env/jms/EmployeeQueue";

    /** EJB remote home JNDI (legacy CORBA-style for remote access) */
    public static final String EJB_REMOTE_JNDI      = "ejb/EmployeeServiceBean";

    // -------------------------------------------------------------------------
    // Application Configuration
    // -------------------------------------------------------------------------
    public static final String APP_NAME             = "WebSphere-EAR-Sample";
    public static final String APP_VERSION          = "1.0.0";

    /** WebSphere WLM workload management transaction class */
    public static final String WLM_TRAN_CLASS       = "CBSU";

    // -------------------------------------------------------------------------
    // Pagination defaults
    // -------------------------------------------------------------------------
    public static final int DEFAULT_PAGE_SIZE       = 25;
    public static final int MAX_PAGE_SIZE           = 200;

    // -------------------------------------------------------------------------
    // Cache settings (used with WebSphere DynaCache / JCache)
    // -------------------------------------------------------------------------
    public static final String DYNACACHE_INSTANCE   = "services/cache/distributedmap";
    public static final int    CACHE_TTL_SECONDS    = 300;
}
