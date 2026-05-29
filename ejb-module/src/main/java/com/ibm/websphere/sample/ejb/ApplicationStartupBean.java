package com.ibm.websphere.sample.ejb;

import com.ibm.websphere.sample.dao.EmployeeDAO;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Singleton Session Bean — Application Startup / In-Memory Cache Manager.
 *
 * WebSphere Best Practices for @Singleton:
 *
 *  1. @Startup: container instantiates this bean when the application starts.
 *     Use for warming up caches, validating configuration, registering MBeans.
 *
 *  2. @ConcurrencyManagement(CONTAINER):
 *     - Default. Container uses read/write locks on methods.
 *     - @Lock(READ) for cache reads — allows concurrent readers.
 *     - @Lock(WRITE) for cache invalidation — exclusive access.
 *
 *  3. @ConcurrencyManagement(BEAN):
 *     - Application manages its own thread safety (e.g., ConcurrentHashMap).
 *     - More flexible but requires careful synchronization.
 *
 *  4. @Schedule (EJB Timer):
 *     - Container-managed persistent timers survive WAS server restart.
 *     - In a cluster, WebSphere ensures only ONE cluster member fires the timer
 *       (via the Scheduler database table). Configure the scheduler data source
 *       in WAS Admin Console > Servers > Scheduler.
 *     - Set persistent=false for non-clustered / in-memory timers.
 *
 *  5. WebSphere HA consideration:
 *     - @Singleton beans are per-JVM; they do NOT replicate state across cluster.
 *     - For distributed caching use WebSphere eXtreme Scale (WXS) / JCache.
 */
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class ApplicationStartupBean {

    private static final Logger LOG = Logger.getLogger(ApplicationStartupBean.class.getName());

    @EJB
    private EmployeeDAO employeeDAO;

    /** Simple in-memory department cache. Not distributed — per JVM only. */
    private final ConcurrentHashMap<String, Long> departmentCountCache = new ConcurrentHashMap<>();

    private long startupTimestamp;

    @PostConstruct
    public void onStartup() {
        LOG.info("=== WebSphere EAR Application Starting ===");
        startupTimestamp = System.currentTimeMillis();

        // Warm up department cache on startup
        try {
            warmDepartmentCache();
            LOG.info("Department cache warmed up with " + departmentCountCache.size() + " entries.");
        } catch (Exception ex) {
            // Non-fatal — log and continue; cache will be populated on first request
            LOG.warning("Cache warm-up failed (non-fatal): " + ex.getMessage());
        }

        LOG.info("=== Application Startup Complete in " +
                 (System.currentTimeMillis() - startupTimestamp) + " ms ===");
    }

    @PreDestroy
    public void onShutdown() {
        LOG.info("=== WebSphere EAR Application Shutting Down ===");
        departmentCountCache.clear();
    }

    // =========================================================================
    // Scheduled Timer — Cache Refresh every 5 minutes
    // =========================================================================

    /**
     * EJB Timer — fires every 5 minutes to refresh the department count cache.
     *
     * WebSphere Scheduler:
     *  - persistent=true (default): timer survives server restart.
     *    WebSphere stores timer state in a Scheduler database (configured via
     *    Admin Console > Servers > Application Servers > your_server > Scheduler).
     *  - In a cluster, WAS uses the database to ensure exactly-once execution
     *    across all cluster members.
     *  - timezone: specify explicitly for global deployments.
     */
    @Schedule(minute = "*/5", hour = "*", persistent = false,
              info = "DepartmentCacheRefreshTimer")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void refreshDepartmentCache(Timer timer) {
        LOG.fine("Scheduled cache refresh triggered.");
        try {
            warmDepartmentCache();
        } catch (Exception ex) {
            LOG.warning("Scheduled cache refresh failed: " + ex.getMessage());
        }
    }

    // =========================================================================
    // Cache Operations — @Lock annotations control concurrency
    // =========================================================================

    @Lock(LockType.READ)
    public Long getCachedDepartmentCount(String department) {
        return departmentCountCache.get(department);
    }

    @Lock(LockType.WRITE)
    public void invalidateDepartmentCache(String department) {
        departmentCountCache.remove(department);
        LOG.fine("Cache invalidated for department: " + department);
    }

    @Lock(LockType.READ)
    public long getApplicationUptimeMs() {
        return System.currentTimeMillis() - startupTimestamp;
    }

    // -------------------------------------------------------------------------
    private void warmDepartmentCache() {
        // In production, fetch distinct departments from DB
        String[] departments = {"Engineering", "Finance", "HR", "Sales", "Operations"};
        for (String dept : departments) {
            try {
                long count = employeeDAO.countByDepartment(dept);
                departmentCountCache.put(dept, count);
            } catch (Exception ex) {
                LOG.fine("Count unavailable for dept=" + dept + ": " + ex.getMessage());
            }
        }
    }
}
