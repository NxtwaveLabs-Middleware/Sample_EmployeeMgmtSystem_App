package com.ibm.websphere.sample.ejb;

import com.ibm.websphere.sample.exception.EmployeeNotFoundException;
import com.ibm.websphere.sample.model.EmployeeDTO;

import javax.ejb.Local;
import java.util.List;

/**
 * EJB Local Business Interface.
 *
 * WebSphere Best Practice:
 *  - @Local interface is used for intra-JVM calls (Servlet → EJB within same EAR).
 *  - Local calls bypass marshaling/unmarshaling; objects are passed by reference.
 *  - Declare @Local on the interface, not on the bean implementation.
 *  - Clients in the same JVM lookup via @EJB injection or
 *    InitialContext.lookup("java:comp/env/ejb/EmployeeServiceLocal").
 */
@Local
public interface EmployeeServiceLocal {

    List<EmployeeDTO> getAllEmployees(int page, int pageSize);

    EmployeeDTO getEmployeeById(Long id) throws EmployeeNotFoundException;

    List<EmployeeDTO> getEmployeesByDepartment(String department);

    List<EmployeeDTO> searchEmployees(String nameLike, String department,
                                      Boolean active, int page, int pageSize);

    List<Object[]> getDepartmentSalarySummary();

    EmployeeDTO createEmployee(EmployeeDTO dto);

    EmployeeDTO updateEmployee(Long id, EmployeeDTO dto) throws EmployeeNotFoundException;

    void deactivateEmployee(Long id) throws EmployeeNotFoundException;

    int deactivateDepartment(String department);
}
