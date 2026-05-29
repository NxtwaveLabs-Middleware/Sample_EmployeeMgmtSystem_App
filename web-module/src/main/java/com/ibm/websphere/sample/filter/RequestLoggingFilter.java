package com.ibm.websphere.sample.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Servlet Filter — Request/Response Logging and Performance Tracking.
 *
 * WebSphere Best Practices:
 *  1. @WebFilter with urlPatterns replaces filter configuration in web.xml.
 *     Web.xml filter entries take precedence if both are present.
 *  2. WebSphere generates SMF (System Management Facility) records for
 *     each HTTP request when performance monitoring is enabled in Admin Console.
 *     This filter adds application-level timing on top.
 *  3. X-Request-ID header pattern: WebSphere's web server plugin inserts a
 *     unique request identifier; propagate it for distributed tracing.
 *  4. WebSphere Activity Log: use java.util.logging (JUL) — WAS routes it
 *     to SystemOut.log and the Activity Log. Avoid Log4j direct file appenders
 *     inside WAS as they bypass WAS log routing.
 */
@WebFilter(
    filterName  = "RequestLoggingFilter",
    urlPatterns = {"/api/*"},
    asyncSupported = true  // required if any servlet uses AsyncContext
)
public class RequestLoggingFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class.getName());
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String START_TIME_ATTR   = "req.startTime";

    @Override
    public void init(FilterConfig filterConfig) {
        LOG.info("RequestLoggingFilter initialized for URL pattern /api/*");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        long startTime = System.currentTimeMillis();
        httpReq.setAttribute(START_TIME_ATTR, startTime);

        // Propagate or generate request correlation ID
        String requestId = httpReq.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = generateRequestId();
        }
        httpResp.setHeader(REQUEST_ID_HEADER, requestId);

        String method = httpReq.getMethod();
        String uri    = httpReq.getRequestURI();
        String query  = httpReq.getQueryString();

        LOG.info("[" + requestId + "] --> " + method + " " + uri +
                 (query != null ? "?" + query : ""));

        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            int  status  = httpResp.getStatus();

            LOG.info("[" + requestId + "] <-- " + status + " " +
                     method + " " + uri + " (" + elapsed + " ms)");

            // WebSphere PMI (Performance Monitoring Infrastructure) custom stats
            // In production, increment PMI counters via PerfMgr API here.
        }
    }

    @Override
    public void destroy() {
        LOG.info("RequestLoggingFilter destroyed.");
    }

    private String generateRequestId() {
        return "req-" + Long.toHexString(System.nanoTime());
    }
}
