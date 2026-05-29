package com.ibm.websphere.sample.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity mapped to the PostgreSQL "employees" table.
 *
 * WebSphere Best Practices:
 *  1. Named queries declared at class level — precompiled by the JPA provider
 *     (OpenJPA on WAS Traditional, EclipseLink on WAS Liberty) at deploy time.
 *  2. Use @TableGenerator (not @SequenceGenerator) when targeting multiple DB vendors;
 *     for PostgreSQL-only deployments @SequenceGenerator is preferred.
 *  3. @Version provides optimistic locking — critical in clustered WebSphere
 *     environments where multiple JVMs can update the same row concurrently.
 *  4. Entity must implement Serializable — required for EJB passivation and
 *     WebSphere HTTP session replication (when stored in session).
 *  5. @EntityListeners with an audit listener demonstrates WAS CDI integration.
 */
@Entity
@Table(name = "employees", schema = "hr",
       indexes = {
           @Index(name = "idx_emp_department", columnList = "department"),
           @Index(name = "idx_emp_email",      columnList = "email", unique = true)
       })
@NamedQueries({
    @NamedQuery(
        name  = Employee.FIND_ALL,
        query = "SELECT e FROM Employee e ORDER BY e.lastName, e.firstName"
    ),
    @NamedQuery(
        name  = Employee.FIND_BY_DEPARTMENT,
        query = "SELECT e FROM Employee e WHERE e.department = :department AND e.active = true ORDER BY e.lastName"
    ),
    @NamedQuery(
        name  = Employee.FIND_BY_EMAIL,
        query = "SELECT e FROM Employee e WHERE e.email = :email"
    ),
    @NamedQuery(
        name  = Employee.COUNT_BY_DEPARTMENT,
        query = "SELECT COUNT(e) FROM Employee e WHERE e.department = :department"
    ),
    @NamedQuery(
        name  = Employee.FIND_ACTIVE,
        query = "SELECT e FROM Employee e WHERE e.active = true ORDER BY e.hireDate DESC"
    ),
    @NamedQuery(
        name  = Employee.FIND_BY_SALARY_RANGE,
        query = "SELECT e FROM Employee e WHERE e.salary BETWEEN :minSalary AND :maxSalary ORDER BY e.salary DESC"
    )
})
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    // Named query constants — prevents typos in calling code
    public static final String FIND_ALL             = "Employee.findAll";
    public static final String FIND_BY_DEPARTMENT   = "Employee.findByDepartment";
    public static final String FIND_BY_EMAIL        = "Employee.findByEmail";
    public static final String COUNT_BY_DEPARTMENT  = "Employee.countByDepartment";
    public static final String FIND_ACTIVE          = "Employee.findActive";
    public static final String FIND_BY_SALARY_RANGE = "Employee.findBySalaryRange";

    // -------------------------------------------------------------------------
    // Primary Key — using PostgreSQL SEQUENCE via @SequenceGenerator
    // -------------------------------------------------------------------------
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "emp_seq")
    @SequenceGenerator(name = "emp_seq", sequenceName = "hr.employee_seq",
                       allocationSize = 50)  // batch of 50 avoids DB round-trips
    @Column(name = "employee_id", nullable = false)
    private Long id;

    // -------------------------------------------------------------------------
    // Basic columns
    // -------------------------------------------------------------------------
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(name = "salary", precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // -------------------------------------------------------------------------
    // Audit columns — populated by @PrePersist / @PreUpdate lifecycle callbacks
    // -------------------------------------------------------------------------
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * @Version — Optimistic Locking.
     * WebSphere clusters: prevents lost-update anomalies when two cluster
     * members load the same row and one commits first.
     */
    @Version
    @Column(name = "version")
    private Long version;

    // -------------------------------------------------------------------------
    // JPA Lifecycle Callbacks
    // -------------------------------------------------------------------------
    @PrePersist
    protected void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------
    public Employee() { }

    public Employee(String firstName, String lastName, String email,
                    String department, String jobTitle, BigDecimal salary,
                    LocalDate hireDate) {
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email;
        this.department = department;
        this.jobTitle   = jobTitle;
        this.salary     = salary;
        this.hireDate   = hireDate;
        this.active     = true;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------
    public Long getId()                         { return id; }
    public String getFirstName()                { return firstName; }
    public void setFirstName(String v)          { this.firstName = v; }
    public String getLastName()                 { return lastName; }
    public void setLastName(String v)           { this.lastName = v; }
    public String getEmail()                    { return email; }
    public void setEmail(String v)              { this.email = v; }
    public String getDepartment()               { return department; }
    public void setDepartment(String v)         { this.department = v; }
    public String getJobTitle()                 { return jobTitle; }
    public void setJobTitle(String v)           { this.jobTitle = v; }
    public BigDecimal getSalary()               { return salary; }
    public void setSalary(BigDecimal v)         { this.salary = v; }
    public LocalDate getHireDate()              { return hireDate; }
    public void setHireDate(LocalDate v)        { this.hireDate = v; }
    public boolean isActive()                   { return active; }
    public void setActive(boolean v)            { this.active = v; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public LocalDateTime getUpdatedAt()         { return updatedAt; }
    public String getCreatedBy()                { return createdBy; }
    public void setCreatedBy(String v)          { this.createdBy = v; }
    public String getUpdatedBy()                { return updatedBy; }
    public void setUpdatedBy(String v)          { this.updatedBy = v; }
    public Long getVersion()                    { return version; }

    @Override
    public String toString() {
        return "Employee{id=" + id + ", name=" + firstName + " " + lastName +
               ", dept=" + department + ", v=" + version + "}";
    }
}
