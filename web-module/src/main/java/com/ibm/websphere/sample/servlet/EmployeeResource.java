package com.ibm.websphere.sample.servlet;

import com.ibm.websphere.sample.ejb.EmployeeServiceLocal;
import com.ibm.websphere.sample.exception.EmployeeNotFoundException;
import com.ibm.websphere.sample.model.EmployeeDTO;
import com.ibm.websphere.sample.util.AppConstants;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JAX-RS REST Resource — Employee API.
 *
 * Exposes CRUD operations for Employee over HTTP.
 *
 * WebSphere Best Practices:
 *
 *  1. @RequestScoped CDI scope:
 *     - JAX-RS resources should be @RequestScoped (or @ApplicationScoped for
 *       stateless resources). @RequestScoped creates one instance per HTTP request.
 *     - Never use @SessionScoped for JAX-RS resources.
 *
 *  2. @EJB injection into JAX-RS:
 *     - WebSphere supports @EJB injection in JAX-RS resources because they are
 *       managed by the CDI/EJB container.
 *     - Prefer @EJB over @Inject for EJBs to get container-managed TX behavior.
 *
 *  3. @Context UriInfo / HttpHeaders / SecurityContext:
 *     - @Context injects JAX-RS contextual objects.
 *     - SecurityContext.getUserPrincipal() integrates with WebSphere security.
 *     - Check isUserInRole() for authorization in the REST layer.
 *
 *  4. Response building:
 *     - Return javax.ws.rs.core.Response for full HTTP control.
 *     - Use proper HTTP status codes: 200 OK, 201 Created, 204 No Content,
 *       404 Not Found, 400 Bad Request, 409 Conflict.
 *     - Set Location header on 201 Created (REST best practice).
 *
 *  5. Media type:
 *     - JSON serialization provided by WebSphere's bundled JSON-B or JSON-P
 *       provider. No Jackson dependency needed on WAS/Liberty.
 *
 *  URL base: /employee-web/api/employees
 */
@Path("/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class EmployeeResource {

    private static final Logger LOG = Logger.getLogger(EmployeeResource.class.getName());

    /** EJB Local reference — injected by WebSphere EJB container */
    @EJB
    private EmployeeServiceLocal employeeService;

    /** JAX-RS context — provides request URI, path params, query params */
    @Context
    private UriInfo uriInfo;

    /** JAX-RS security context — access caller principal and roles */
    @Context
    private SecurityContext securityContext;

    // =========================================================================
    // GET /employees?page=1&pageSize=25
    // =========================================================================

    /**
     * Retrieve paginated list of all employees.
     * WebSphere: No @RolesAllowed needed here if handled in ejb-jar.xml.
     * For defense-in-depth, annotate here AND in ejb-jar.xml.
     */
    @GET
    public Response getAllEmployees(
            @QueryParam("page")     @DefaultValue("1")  int page,
            @QueryParam("pageSize") @DefaultValue("25") int pageSize) {

        // Clamp page size to prevent large result sets
        pageSize = Math.min(pageSize, AppConstants.MAX_PAGE_SIZE);
        if (page < 1) page = 1;

        LOG.fine("GET /employees page=" + page + " pageSize=" + pageSize);

        List<EmployeeDTO> employees = employeeService.getAllEmployees(page, pageSize);

        return Response.ok(employees)
                       .header("X-Page-Number", page)
                       .header("X-Page-Size", pageSize)
                       .header("X-Result-Count", employees.size())
                       .build();
    }

    // =========================================================================
    // GET /employees/{id}
    // =========================================================================
    @GET
    @Path("/{id}")
    public Response getEmployeeById(@PathParam("id") Long id) {
        LOG.fine("GET /employees/" + id);
        try {
            EmployeeDTO emp = employeeService.getEmployeeById(id);
            return Response.ok(emp).build();
        } catch (EmployeeNotFoundException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(new ErrorResponse("NOT_FOUND", ex.getMessage()))
                           .build();
        }
    }

    // =========================================================================
    // GET /employees/department/{dept}
    // =========================================================================
    @GET
    @Path("/department/{department}")
    public Response getByDepartment(@PathParam("department") String department) {
        List<EmployeeDTO> employees = employeeService.getEmployeesByDepartment(department);
        return Response.ok(employees).build();
    }

    // =========================================================================
    // GET /employees/search?name=john&department=Engineering&active=true&page=1
    // =========================================================================
    @GET
    @Path("/search")
    public Response searchEmployees(
            @QueryParam("name")       String  nameLike,
            @QueryParam("department") String  department,
            @QueryParam("active")     Boolean active,
            @QueryParam("page")       @DefaultValue("1")  int page,
            @QueryParam("pageSize")   @DefaultValue("25") int pageSize) {

        List<EmployeeDTO> results = employeeService.searchEmployees(
            nameLike, department, active, page, Math.min(pageSize, AppConstants.MAX_PAGE_SIZE));

        return Response.ok(results).build();
    }

    // =========================================================================
    // GET /employees/reports/salary-summary
    // =========================================================================
    @GET
    @Path("/reports/salary-summary")
    public Response getDepartmentSalarySummary() {
        List<Object[]> summary = employeeService.getDepartmentSalarySummary();
        return Response.ok(summary).build();
    }

    // =========================================================================
    // POST /employees
    // =========================================================================
    @POST
    public Response createEmployee(EmployeeDTO employeeDTO) {
        if (employeeDTO == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("BAD_REQUEST", "Request body required"))
                           .build();
        }

        // Authorization: check caller's role via WAS SecurityContext
        String caller = securityContext.getUserPrincipal() != null
                        ? securityContext.getUserPrincipal().getName() : "anonymous";
        LOG.info("Creating employee, caller=" + caller);

        try {
            EmployeeDTO created = employeeService.createEmployee(employeeDTO);

            // Build Location header pointing to the new resource
            URI location = uriInfo.getAbsolutePathBuilder()
                                   .path(String.valueOf(created.getId()))
                                   .build();

            return Response.created(location)
                           .entity(created)
                           .build();

        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.CONFLICT)
                           .entity(new ErrorResponse("CONFLICT", ex.getMessage()))
                           .build();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error creating employee", ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity(new ErrorResponse("SERVER_ERROR", "An unexpected error occurred"))
                           .build();
        }
    }

    // =========================================================================
    // PUT /employees/{id}
    // =========================================================================
    @PUT
    @Path("/{id}")
    public Response updateEmployee(@PathParam("id") Long id, EmployeeDTO employeeDTO) {
        try {
            EmployeeDTO updated = employeeService.updateEmployee(id, employeeDTO);
            return Response.ok(updated).build();
        } catch (EmployeeNotFoundException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(new ErrorResponse("NOT_FOUND", ex.getMessage()))
                           .build();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error updating employee id=" + id, ex);
            return Response.serverError()
                           .entity(new ErrorResponse("SERVER_ERROR", "Update failed"))
                           .build();
        }
    }

    // =========================================================================
    // DELETE /employees/{id}  (soft delete)
    // =========================================================================
    @DELETE
    @Path("/{id}")
    public Response deactivateEmployee(@PathParam("id") Long id) {
        try {
            employeeService.deactivateEmployee(id);
            return Response.noContent().build(); // 204 No Content
        } catch (EmployeeNotFoundException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(new ErrorResponse("NOT_FOUND", ex.getMessage()))
                           .build();
        }
    }

    // =========================================================================
    // DELETE /employees/department/{dept}  (bulk soft delete — Admin only)
    // =========================================================================
    @DELETE
    @Path("/department/{department}")
    public Response deactivateDepartment(@PathParam("department") String department) {

        // WebSphere security check — must be AdminRole
        if (!securityContext.isUserInRole("AdminRole")) {
            return Response.status(Response.Status.FORBIDDEN)
                           .entity(new ErrorResponse("FORBIDDEN",
                               "Admin role required for bulk operations"))
                           .build();
        }

        int count = employeeService.deactivateDepartment(department);
        return Response.ok(new OperationResult("Deactivated " + count + " employees", count))
                       .build();
    }

    // =========================================================================
    // Inner classes for structured JSON responses
    // =========================================================================

    /** Standard error response body */
    public static class ErrorResponse {
        private String code;
        private String message;
        public ErrorResponse(String code, String message) {
            this.code = code; this.message = message;
        }
        public String getCode()    { return code; }
        public String getMessage() { return message; }
    }

    /** Standard operation result body */
    public static class OperationResult {
        private String message;
        private int    count;
        public OperationResult(String message, int count) {
            this.message = message; this.count = count;
        }
        public String getMessage() { return message; }
        public int    getCount()   { return count; }
    }
}
