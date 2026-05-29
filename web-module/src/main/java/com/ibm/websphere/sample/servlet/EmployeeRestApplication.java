package com.ibm.websphere.sample.servlet;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * JAX-RS Application Activator.
 *
 * WebSphere Best Practices:
 *  - @ApplicationPath defines the root URL path for all REST resources.
 *  - On WebSphere Traditional 8.5.5+, JAX-RS 2.0 (Apache CXF) is provided.
 *  - On Liberty, JAX-RS 2.1 (Apache CXF) is provided via the jaxrs-2.1 feature.
 *  - Extending javax.ws.rs.core.Application with no overrides tells the container
 *    to auto-scan for @Path-annotated classes in the WAR — no explicit registration needed.
 *  - If you extend getClasses() or getSingletons(), auto-scanning is disabled.
 *
 * URL mapping: http://host:port/employee-web/api/*
 */
@ApplicationPath("/api")
public class EmployeeRestApplication extends Application {
    // Empty body — container auto-discovers @Path resources
}
