package com.ibm.websphere.sample.ejb;

import com.ibm.websphere.sample.dao.EmployeeDAO;
import com.ibm.websphere.sample.entity.Employee;
import com.ibm.websphere.sample.exception.DataAccessException;
import com.ibm.websphere.sample.exception.EmployeeNotFoundException;
import com.ibm.websphere.sample.model.EmployeeDTO;
import com.ibm.websphere.sample.util.AppConstants;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.jms.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless Session Bean — Employee Service.
 *
 * This is the primary business façade for all employee operations.
 *
 * WebSphere Best Practices Demonstrated:
 *
 *  1. LOCAL and REMOTE interfaces:
 *     - @Local  : used by WAR servlets/JAX-RS within the same JVM (no marshaling).
 *     - @Remote : exposed for CORBA/RMI clients on other JVMs (full marshaling).
 *     - Always prefer @Local for intra-EAR calls — it avoids serialization cost.
 *
 *  2. TRANSACTION MANAGEMENT (CMT — Container Managed Transactions):
 *     - Default is REQUIRED. Explicitly set REQUIRES_NEW where you need a
 *       nested/independent TX (e.g., audit logging that must commit regardless).
 *     - setRollbackOnly() signals the container to roll back without throwing.
 *
 *  3. JMS INTEGRATION (WebSphere Service Integration Bus or MQ):
 *     - @Resource injection for JMS ConnectionFactory and Queue.
 *     - Send messages asynchronously to avoid synchronous coupling.
 *     - Use try-with-resources for JMS objects to guarantee close().
 *
 *  4. SESSION CONTEXT (@Resource SessionContext):
 *     - Access caller principal for security checks.
 *     - Call setRollbackOnly() for programmatic TX rollback without exception.
 *     - getBusinessObject() to get the EJB proxy (needed for self-invocation
 *       with TX interception — never call this.method() directly for TX to fire).
 *
 *  5. EXCEPTION HANDLING:
 *     - @ApplicationException(rollback=true): container rolls back on throw.
 *     - System exceptions (RuntimeException) always cause rollback + bean discard.
 *     - Wrap checked exceptions in @ApplicationException to control rollback.
 *
 *  6. EJB TIMER SERVICE (in AppTimerBean) — @Schedule for periodic tasks.
 */
@Stateless(name = "EmployeeServiceBean", mappedName = "ejb/EmployeeServiceBean")
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class EmployeeServiceBean implements EmployeeServiceLocal, EmployeeServiceRemote {

    private static final Logger LOG = Logger.getLogger(EmployeeServiceBean.class.getName());

    /** EJB container's SessionContext — WebSphere provides this at runtime. */
    @Resource
    private SessionContext sessionContext;

    /** DAO injected as EJB (not CDI bean) to ensure TX propagation. */
    @EJB
    private EmployeeDAO employeeDAO;

    /**
     * JMS ConnectionFactory — configured in WebSphere Admin Console:
     *   Resources > JMS > Connection Factories
     * Bound to JNDI: java:comp/env/jms/EmployeeQCF
     * resource-ref in ejb-jar.xml maps the logical name to the global JNDI.
     */
    @Resource(lookup = AppConstants.JMS_CF_JNDI)
    private ConnectionFactory jmsConnectionFactory;

    /**
     * JMS Queue — configured in WebSphere Admin Console:
     *   Resources > JMS > Queues
     */
    @Resource(lookup = AppConstants.JMS_QUEUE_JNDI)
    private Queue employeeQueue;

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<EmployeeDTO> getAllEmployees(int page, int pageSize) {
        LOG.fine("getAllEmployees page=" + page);
        List<Employee> employees = employeeDAO.findAll(page, pageSize);
        return employees.stream()
                        .map(this::toDTO)
                        .collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public EmployeeDTO getEmployeeById(Long id) throws EmployeeNotFoundException {
        return employeeDAO.findById(id)
                          .map(this::toDTO)
                          .orElseThrow(() -> new EmployeeNotFoundException(
                              "Employee not found with id=" + id));
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<EmployeeDTO> getEmployeesByDepartment(String department) {
        return employeeDAO.findByDepartment(department)
                          .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<EmployeeDTO> searchEmployees(String nameLike, String department,
                                             Boolean active, int page, int pageSize) {
        return employeeDAO.searchEmployees(nameLike, department, active, page, pageSize)
                          .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Object[]> getDepartmentSalarySummary() {
        return employeeDAO.getDepartmentSalarySummary();
    }

    // =========================================================================
    // WRITE OPERATIONS
    // =========================================================================

    /**
     * Create a new employee and send an async JMS notification.
     *
     * TX behaviour:
     *  - REQUIRED (inherited): DB insert and JMS send both participate in the
     *    same XA transaction. If JMS send fails, the DB insert rolls back too.
     *  - This is two-phase commit (2PC) XA — WebSphere Transaction Manager
     *    coordinates both the JDBC DataSource and JMS provider atomically.
     *    Configure the DataSource with XA support in WAS Admin Console.
     */
    @Override
    public EmployeeDTO createEmployee(EmployeeDTO dto) {
        LOG.info("Creating employee: " + dto.getEmail());

        // Business validation
        if (employeeDAO.findByEmail(dto.getEmail()).isPresent()) {
            // Mark TX for rollback — no need to throw
            sessionContext.setRollbackOnly();
            throw new IllegalArgumentException(
                "Employee already exists with email: " + dto.getEmail());
        }

        Employee entity = toEntity(dto);
        entity.setCreatedBy(getCallerPrincipal());

        Employee saved = employeeDAO.create(entity);
        EmployeeDTO result = toDTO(saved);

        // Async JMS notification — participates in same XA TX
        sendJmsNotification("CREATED", saved.getId(), saved.getEmail());

        return result;
    }

    /**
     * Update employee — uses REQUIRES_NEW to ensure audit is committed
     * independently if the outer TX rolls back.
     *
     * NOTE: In practice, consider separating audit into a dedicated bean
     * with REQUIRES_NEW to avoid coupling to the main TX.
     */
    @Override
    public EmployeeDTO updateEmployee(Long id, EmployeeDTO dto) throws EmployeeNotFoundException {
        LOG.info("Updating employee id=" + id);

        Employee existing = employeeDAO.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found: " + id));

        // Merge fields from DTO
        existing.setFirstName(dto.getFirstName());
        existing.setLastName(dto.getLastName());
        existing.setDepartment(dto.getDepartment());
        existing.setJobTitle(dto.getJobTitle());
        existing.setSalary(dto.getSalary());
        existing.setHireDate(dto.getHireDate());
        existing.setUpdatedBy(getCallerPrincipal());

        Employee updated = employeeDAO.update(existing);
        sendJmsNotification("UPDATED", updated.getId(), updated.getEmail());
        return toDTO(updated);
    }

    @Override
    public void deactivateEmployee(Long id) throws EmployeeNotFoundException {
        LOG.info("Deactivating employee id=" + id);
        // Validate existence before deactivating
        employeeDAO.findById(id)
                   .orElseThrow(() -> new EmployeeNotFoundException("Employee not found: " + id));
        employeeDAO.softDelete(id);
        sendJmsNotification("DEACTIVATED", id, "");
    }

    @Override
    public int deactivateDepartment(String department) {
        LOG.warning("Bulk deactivation for department: " + department);
        int count = employeeDAO.deactivateDepartment(department);
        sendJmsNotification("DEPT_DEACTIVATED", null, department);
        return count;
    }

    // =========================================================================
    // JMS Helper — send async event to Service Integration Bus
    // =========================================================================

    /**
     * Send JMS TextMessage to the Employee processing queue.
     *
     * WebSphere Best Practice:
     *  - Use try-with-resources (JMS 2.0 simplified API) when available,
     *    or classic try/finally to guarantee connection.close().
     *  - On WebSphere Traditional (SIBus), the JMS provider is built-in.
     *    On Liberty, configure a JMS RA or use MQ RA.
     *  - XA-aware: if jmsConnectionFactory is XA-capable, this send
     *    participates in the current JTA transaction automatically.
     */
    private void sendJmsNotification(String eventType, Long empId, String email) {
        if (jmsConnectionFactory == null) {
            LOG.warning("JMS ConnectionFactory not available — skipping notification");
            return;
        }
        try (Connection conn = jmsConnectionFactory.createConnection();
             Session    sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            TextMessage msg = sess.createTextMessage();
            msg.setStringProperty("eventType", eventType);
            msg.setStringProperty("email",     email);
            if (empId != null) msg.setLongProperty("employeeId", empId);
            msg.setText("{\"event\":\"" + eventType + "\",\"employeeId\":" + empId + "}");

            MessageProducer producer = sess.createProducer(employeeQueue);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT); // survive WAS restart
            producer.send(msg);
            LOG.info("JMS event sent: " + eventType + " for empId=" + empId);

        } catch (JMSException ex) {
            // Log but don't rethrow — avoid rolling back DB TX for JMS failure
            // In production, consider dead-letter-queue (DLQ) pattern here.
            LOG.log(Level.WARNING, "JMS notification failed for event=" + eventType, ex);
        }
    }

    // =========================================================================
    // WebSphere Security Helper
    // =========================================================================

    /**
     * Get the authenticated caller's principal name from EJB container.
     * In WebSphere, this integrates with LDAP / RACF / TAM security realms.
     */
    private String getCallerPrincipal() {
        try {
            return sessionContext.getCallerPrincipal().getName();
        } catch (Exception ex) {
            return "SYSTEM";
        }
    }

    // =========================================================================
    // Mapping helpers
    // =========================================================================
    private EmployeeDTO toDTO(Employee e) {
        return new EmployeeDTO(e.getId(), e.getFirstName(), e.getLastName(),
                               e.getEmail(), e.getDepartment(), e.getJobTitle(),
                               e.getSalary(), e.getHireDate(), e.isActive());
    }

    private Employee toEntity(EmployeeDTO dto) {
        return new Employee(dto.getFirstName(), dto.getLastName(), dto.getEmail(),
                            dto.getDepartment(), dto.getJobTitle(), dto.getSalary(),
                            dto.getHireDate());
    }
}
