package com.ibm.websphere.sample.dao;

import com.ibm.websphere.sample.entity.Employee;
import com.ibm.websphere.sample.exception.DataAccessException;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for Employee entity using JPA.
 *
 * WebSphere Best Practices:
 *
 *  1. STATELESS EJB as DAO:
 *     - Stateless Session Beans are pooled by WebSphere; no per-client state
 *       is held between method calls, making them efficient for DB access.
 *
 *  2. PERSISTENCE UNIT INJECTION:
 *     - @PersistenceContext injects a container-managed EntityManager.
 *     - WebSphere manages the EntityManager lifecycle and transaction enrollment
 *       automatically — never call em.close() on a container-managed EM.
 *     - unitName must match <persistence-unit> in persistence.xml.
 *
 *  3. TRANSACTION ATTRIBUTES:
 *     - REQUIRED (default): joins caller's TX or starts new one. Used for writes.
 *     - SUPPORTS: uses caller's TX if present, no TX otherwise. Good for reads
 *       that must participate in a caller's TX (e.g., read-then-write pattern).
 *     - NOT_SUPPORTED: suspends any active TX. Used for non-transactional reads
 *       to avoid locking rows unnecessarily — improves throughput on WAS.
 *
 *  4. NAMED QUERIES vs JPQL strings:
 *     - Named queries are pre-compiled at deploy time by OpenJPA/EclipseLink,
 *       providing better performance than building JPQL strings at runtime.
 *
 *  5. PAGINATION via setFirstResult/setMaxResults:
 *     - Maps to SQL OFFSET/LIMIT on PostgreSQL; avoids loading entire tables.
 *
 *  6. WebSphere JDBC Statement caching:
 *     - The DataSource in WAS can be configured with statement caching (Admin
 *       Console > JDBC > DataSources > Advanced > Statement cache size).
 *       NamedQueries benefit from this because their SQL form is stable.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class EmployeeDAO {

    private static final Logger LOG = Logger.getLogger(EmployeeDAO.class.getName());

    /**
     * Container-managed EntityManager.
     * unitName maps to persistence.xml <persistence-unit name="EmployeePU">.
     * WebSphere binds the DataSource via the jta-data-source JNDI reference.
     */
    @PersistenceContext(unitName = "EmployeePU")
    private EntityManager em;

    // =========================================================================
    // READ operations — use NOT_SUPPORTED to avoid row-level locking for reads
    // =========================================================================

    /**
     * Find all employees — paginated.
     * TX = NOT_SUPPORTED: read-only, no need to enlist in a transaction.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Employee> findAll(int pageNumber, int pageSize) {
        LOG.fine("findAll page=" + pageNumber + " size=" + pageSize);
        return em.createNamedQuery(Employee.FIND_ALL, Employee.class)
                 .setFirstResult((pageNumber - 1) * pageSize)
                 .setMaxResults(pageSize)
                 .setHint("javax.persistence.query.timeout", 30000) // 30s query timeout
                 .getResultList();
    }

    /**
     * Find employee by primary key.
     * Returns Optional — avoids null returns in service layer.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Optional<Employee> findById(Long id) {
        if (id == null) return Optional.empty();
        Employee emp = em.find(Employee.class, id);
        return Optional.ofNullable(emp);
    }

    /**
     * Find employees by department with active filter.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Employee> findByDepartment(String department) {
        return em.createNamedQuery(Employee.FIND_BY_DEPARTMENT, Employee.class)
                 .setParameter("department", department)
                 .getResultList();
    }

    /**
     * Find employee by unique email.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Optional<Employee> findByEmail(String email) {
        try {
            Employee emp = em.createNamedQuery(Employee.FIND_BY_EMAIL, Employee.class)
                             .setParameter("email", email)
                             .getSingleResult();
            return Optional.of(emp);
        } catch (NoResultException ex) {
            return Optional.empty();
        }
    }

    /**
     * Find employees within salary range.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Employee> findBySalaryRange(BigDecimal min, BigDecimal max) {
        return em.createNamedQuery(Employee.FIND_BY_SALARY_RANGE, Employee.class)
                 .setParameter("minSalary", min)
                 .setParameter("maxSalary", max)
                 .getResultList();
    }

    /**
     * Count employees in department (scalar query).
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long countByDepartment(String department) {
        return em.createNamedQuery(Employee.COUNT_BY_DEPARTMENT, Long.class)
                 .setParameter("department", department)
                 .getSingleResult();
    }

    /**
     * Criteria API example — dynamic search with optional filters.
     * Demonstrates WebSphere JPA Criteria API for type-safe queries.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<Employee> searchEmployees(String nameLike, String department,
                                          Boolean active, int page, int pageSize) {
        // Build a dynamic JPQL query string (Criteria API alternative)
        StringBuilder jpql = new StringBuilder("SELECT e FROM Employee e WHERE 1=1");
        if (nameLike  != null) jpql.append(" AND (LOWER(e.firstName) LIKE :name OR LOWER(e.lastName) LIKE :name)");
        if (department!= null) jpql.append(" AND e.department = :dept");
        if (active    != null) jpql.append(" AND e.active = :active");
        jpql.append(" ORDER BY e.lastName, e.firstName");

        Query q = em.createQuery(jpql.toString());
        if (nameLike  != null) q.setParameter("name", "%" + nameLike.toLowerCase() + "%");
        if (department!= null) q.setParameter("dept", department);
        if (active    != null) q.setParameter("active", active);

        return q.setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    // =========================================================================
    // WRITE operations — participate in caller's transaction (REQUIRED default)
    // =========================================================================

    /**
     * Persist new employee.
     * WebSphere will auto-enlist the JPA EntityManager in the JTA transaction
     * managed by the EJB container. No explicit tx.begin()/commit() needed.
     */
    public Employee create(Employee employee) {
        LOG.info("Creating employee: " + employee.getEmail());
        em.persist(employee);
        em.flush(); // flush to DB within TX to catch constraint violations early
        LOG.info("Employee created with id=" + employee.getId());
        return employee;
    }

    /**
     * Merge (update) existing employee.
     * Returns the managed entity returned by merge — callers must use the
     * returned reference (the passed-in entity may be detached).
     */
    public Employee update(Employee employee) {
        LOG.info("Updating employee id=" + employee.getId());
        Employee managed = em.merge(employee);
        em.flush();
        return managed;
    }

    /**
     * Soft-delete: set active=false. Preferred over physical DELETE to
     * preserve audit history.
     */
    public void softDelete(Long id) {
        Employee emp = em.find(Employee.class, id);
        if (emp != null) {
            emp.setActive(false);
            em.merge(emp);
            LOG.info("Soft-deleted employee id=" + id);
        }
    }

    /**
     * Bulk update using JPQL UPDATE — bypasses individual entity loading.
     * WebSphere Best Practice: for large datasets use bulk DML; the JPA
     * provider will not load entities into memory.
     * NOTE: after bulk update, clear the second-level cache if enabled.
     */
    public int deactivateDepartment(String department) {
        int updated = em.createQuery(
            "UPDATE Employee e SET e.active = false WHERE e.department = :dept")
            .setParameter("dept", department)
            .executeUpdate();
        LOG.info("Deactivated " + updated + " employees in dept=" + department);
        return updated;
    }

    /**
     * JPQL Native SQL for PostgreSQL-specific operations.
     * Use sparingly; prefer JPQL/Criteria for portability.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<Object[]> getDepartmentSalarySummary() {
        return em.createNativeQuery(
            "SELECT department, " +
            "       COUNT(*) AS emp_count, " +
            "       AVG(salary)::numeric(12,2) AS avg_salary, " +
            "       MAX(salary) AS max_salary, " +
            "       MIN(salary) AS min_salary " +
            "FROM hr.employees " +
            "WHERE active = true " +
            "GROUP BY department " +
            "ORDER BY avg_salary DESC")
            .getResultList();
    }
}
