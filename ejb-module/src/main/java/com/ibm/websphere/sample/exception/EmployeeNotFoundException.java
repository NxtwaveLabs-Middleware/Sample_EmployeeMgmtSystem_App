package com.ibm.websphere.sample.exception;

import javax.ejb.ApplicationException;

/**
 * Thrown when an employee cannot be found.
 *
 * WebSphere Best Practice — @ApplicationException:
 *  - rollback=false (default): container does NOT roll back the TX when this
 *    exception is thrown. The calling code can catch it and continue.
 *  - rollback=true: container rolls back the TX automatically on throw.
 *    Use this for business rule violations that should abort the operation.
 *  - Without @ApplicationException, a checked exception does NOT trigger
 *    TX rollback; an unchecked (Runtime) exception ALWAYS rolls back.
 *  - Declared on the interface method; clients must catch it.
 */
@ApplicationException(rollback = false)
public class EmployeeNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public EmployeeNotFoundException(String message) {
        super(message);
    }

    public EmployeeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
