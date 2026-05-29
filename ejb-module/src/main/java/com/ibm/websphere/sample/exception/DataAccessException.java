package com.ibm.websphere.sample.exception;

import javax.ejb.ApplicationException;

/**
 * System-level data access exception.
 * rollback=true: always rolls back the JTA transaction on throw.
 */
@ApplicationException(rollback = true)
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
