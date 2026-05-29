package com.ibm.websphere.sample.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object for Employee.
 *
 * WebSphere Best Practice:
 *  - Implement Serializable for EJB remote interfaces and HTTP session replication.
 *  - Keep DTOs in a shared JAR (shared-lib) accessible to both EJB and WAR modules
 *    without circular dependencies. In WebSphere, place the shared JAR in the EAR's
 *    root and declare it in MANIFEST.MF Class-Path entries.
 *  - Use explicit serialVersionUID to survive class evolution across cluster nodes.
 */
public class EmployeeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long   id;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private String jobTitle;
    private BigDecimal salary;
    private LocalDate hireDate;
    private boolean active;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------
    public EmployeeDTO() { }

    public EmployeeDTO(Long id, String firstName, String lastName,
                       String email, String department, String jobTitle,
                       BigDecimal salary, LocalDate hireDate, boolean active) {
        this.id         = id;
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email;
        this.department = department;
        this.jobTitle   = jobTitle;
        this.salary     = salary;
        this.hireDate   = hireDate;
        this.active     = active;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------
    public Long getId()                     { return id; }
    public void setId(Long id)              { this.id = id; }

    public String getFirstName()            { return firstName; }
    public void setFirstName(String v)      { this.firstName = v; }

    public String getLastName()             { return lastName; }
    public void setLastName(String v)       { this.lastName = v; }

    public String getEmail()                { return email; }
    public void setEmail(String v)          { this.email = v; }

    public String getDepartment()           { return department; }
    public void setDepartment(String v)     { this.department = v; }

    public String getJobTitle()             { return jobTitle; }
    public void setJobTitle(String v)       { this.jobTitle = v; }

    public BigDecimal getSalary()           { return salary; }
    public void setSalary(BigDecimal v)     { this.salary = v; }

    public LocalDate getHireDate()          { return hireDate; }
    public void setHireDate(LocalDate v)    { this.hireDate = v; }

    public boolean isActive()               { return active; }
    public void setActive(boolean v)        { this.active = v; }

    @Override
    public String toString() {
        return "EmployeeDTO{id=" + id + ", name=" + firstName + " " + lastName +
               ", dept=" + department + ", active=" + active + "}";
    }
}
