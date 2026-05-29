package com.ibm.websphere.sample.listener;

import com.ibm.websphere.sample.util.AppConstants;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ServletContextListener — Application Lifecycle Hooks.
 *
 * WebSphere Best Practices:
 *  1. @WebListener is portable (Servlet 3.0+). An entry in web.xml is equivalent.
 *  2. contextInitialized:
 *     - Validate required JNDI resources are available (fail-fast on startup).
 *     - Store application-wide config in ServletContext attributes.
 *     - Log WebSphere server and app metadata for diagnostic purposes.
 *  3. contextDestroyed:
 *     - Release resources not managed by the container (thread pools, caches).
 *     - Do NOT close container-managed DataSources or EJBs here.
 *  4. JNDI lookup:
 *     - Always use java:comp/env/ prefix inside a Java EE component.
 *     - A lookup without the prefix goes to the global JNDI namespace —
 *       works but is less portable and bypasses resource-ref mapping.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();

        LOG.info("========================================");
        LOG.info("  " + AppConstants.APP_NAME + " v" + AppConstants.APP_VERSION + " starting...");
        LOG.info("  Context root: " + ctx.getContextPath());
        LOG.info("  Servlet spec: " + ctx.getMajorVersion() + "." + ctx.getMinorVersion());
        LOG.info("  Server info:  " + ctx.getServerInfo());
        LOG.info("========================================");

        // Validate PostgreSQL DataSource on startup — fail fast rather than
        // serving requests with a broken DB connection
        validateDataSource(ctx);

        // Store app metadata in context for access from servlets/JAX-RS
        ctx.setAttribute("appName",    AppConstants.APP_NAME);
        ctx.setAttribute("appVersion", AppConstants.APP_VERSION);
        ctx.setAttribute("startTime",  System.currentTimeMillis());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOG.info(AppConstants.APP_NAME + " shutting down — context destroyed.");
    }

    // -------------------------------------------------------------------------
    private void validateDataSource(ServletContext ctx) {
        try {
            InitialContext ic = new InitialContext();
            DataSource ds = (DataSource) ic.lookup(AppConstants.DS_JNDI);

            if (ds == null) {
                LOG.severe("DataSource is NULL at JNDI: " + AppConstants.DS_JNDI);
                return;
            }

            // Test connectivity and log DB metadata
            try (Connection conn = ds.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                LOG.info("DataSource OK — Database: " + meta.getDatabaseProductName() +
                         " " + meta.getDatabaseProductVersion());
                LOG.info("JDBC Driver: " + meta.getDriverName() +
                         " " + meta.getDriverVersion());
                LOG.info("DB URL: " + meta.getURL());
                // Store DB version for health endpoint
                ctx.setAttribute("dbVersion", meta.getDatabaseProductVersion());
            }

        } catch (NamingException ex) {
            LOG.log(Level.SEVERE,
                "DataSource not found at JNDI: " + AppConstants.DS_JNDI +
                ". Ensure the DataSource is configured in WebSphere Admin Console " +
                "(Resources > JDBC > Data Sources) and the resource-ref is bound correctly.", ex);
        } catch (SQLException ex) {
            LOG.log(Level.WARNING,
                "DataSource found but connection test failed. Check PostgreSQL server, " +
                "credentials, and connection pool settings in WebSphere Admin Console.", ex);
        }
    }
}
