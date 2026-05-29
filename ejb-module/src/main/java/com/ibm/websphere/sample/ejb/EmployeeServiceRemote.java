package com.ibm.websphere.sample.ejb;

import com.ibm.websphere.sample.exception.EmployeeNotFoundException;
import com.ibm.websphere.sample.model.EmployeeDTO;

import javax.ejb.Remote;
import java.util.List;

/**
 * EJB Remote Business Interface — for cross-JVM access via CORBA/IIOP.
 *
 * WebSphere Best Practices:
 *  - Remote interfaces add marshaling overhead; prefer @Local for intra-EAR.
 *  - All parameter and return types MUST be Serializable (hence EmployeeDTO
 *    implements Serializable in shared-lib).
 *  - WebSphere generates stub/tie classes during application deployment.
 *  - Remote clients look up via:
 *      Properties props = new Properties();
 *      props.put(Context.INITIAL_CONTEXT_FACTORY,
 *                "com.ibm.websphere.naming.WsnInitialContextFactory");
 *      props.put(Context.PROVIDER_URL, "iiop://was-host:2809");
 *      InitialContext ctx = new InitialContext(props);
 *      EmployeeServiceRemote svc =
 *          (EmployeeServiceRemote) ctx.lookup("ejb/EmployeeServiceBean");
 *  - In a clustered WAS environment, the PROVIDER_URL can point to the
 *    Name Server Cluster (bootstrap port) for automatic workload balancing.
 */
@Remote
public interface EmployeeServiceRemote {

    List<EmployeeDTO> getAllEmployees(int page, int pageSize);

    EmployeeDTO getEmployeeById(Long id) throws EmployeeNotFoundException;

    List<EmployeeDTO> getEmployeesByDepartment(String department);

    EmployeeDTO createEmployee(EmployeeDTO dto);

    EmployeeDTO updateEmployee(Long id, EmployeeDTO dto) throws EmployeeNotFoundException;

    void deactivateEmployee(Long id) throws EmployeeNotFoundException;
}
