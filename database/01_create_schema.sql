-- =============================================================================
-- PostgreSQL Schema — Employee HR Database
-- Compatible with: PostgreSQL 12+
-- Target WebSphere DataSource: jdbc/EmployeePostgresDS
-- =============================================================================

-- Create dedicated schema for HR data (isolation best practice)
CREATE SCHEMA IF NOT EXISTS hr AUTHORIZATION postgres;

-- Grant schema privileges to the application DB user
-- Replace 'wasuser' with your WebSphere DataSource DB username
CREATE USER IF NOT EXISTS wasuser WITH PASSWORD 'SecurePass123!';
GRANT USAGE  ON SCHEMA hr TO wasuser;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA hr TO wasuser;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA hr TO wasuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA hr
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO wasuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA hr
    GRANT USAGE, SELECT                  ON SEQUENCES TO wasuser;

-- =============================================================================
-- Sequence for Employee primary key (JPA @SequenceGenerator with allocationSize=50)
-- allocationSize=50 means WAS fetches 50 IDs at once (batch allocation).
-- The sequence increments by 50 in the DB; WAS assigns IDs [N, N+49] in memory.
-- =============================================================================
CREATE SEQUENCE IF NOT EXISTS hr.employee_seq
    START WITH 1
    INCREMENT BY 50       -- MUST match JPA @SequenceGenerator allocationSize
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- =============================================================================
-- Employees Table
-- =============================================================================
CREATE TABLE IF NOT EXISTS hr.employees (
    employee_id   BIGINT        NOT NULL DEFAULT nextval('hr.employee_seq'),
    first_name    VARCHAR(100)  NOT NULL,
    last_name     VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    department    VARCHAR(100)  NOT NULL,
    job_title     VARCHAR(150),
    salary        NUMERIC(12,2),
    hire_date     DATE,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,

    -- Audit columns (populated by JPA @PrePersist / @PreUpdate)
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),

    -- Optimistic locking version column (JPA @Version)
    -- Starts at NULL; JPA sets it to 0 on first persist, increments on each update.
    version       BIGINT,

    CONSTRAINT pk_employees PRIMARY KEY (employee_id),
    CONSTRAINT uq_employees_email UNIQUE (email),
    CONSTRAINT ck_employees_salary CHECK (salary IS NULL OR salary >= 0)
);

COMMENT ON TABLE  hr.employees             IS 'Employee master record — managed by WebSphere EAR JPA';
COMMENT ON COLUMN hr.employees.version     IS 'JPA optimistic locking version counter';
COMMENT ON COLUMN hr.employees.active      IS 'Soft-delete flag; false = logically deleted';

-- =============================================================================
-- Performance Indexes
-- =============================================================================

-- Department index — frequent filter in NamedQuery findByDepartment
CREATE INDEX IF NOT EXISTS idx_emp_department
    ON hr.employees (department)
    WHERE active = TRUE;   -- Partial index: only active employees (PostgreSQL feature)

-- Email index for unique lookup (covered by UNIQUE constraint above)
-- CREATE UNIQUE INDEX IF NOT EXISTS idx_emp_email ON hr.employees (email);  -- already via UNIQUE

-- Hire date index for range queries / sorted pagination
CREATE INDEX IF NOT EXISTS idx_emp_hire_date
    ON hr.employees (hire_date DESC);

-- Composite index for salary range queries
CREATE INDEX IF NOT EXISTS idx_emp_salary_active
    ON hr.employees (salary, active);

-- GIN index on first/last name for ILIKE pattern matching (PostgreSQL-specific)
-- Required for searchEmployees() LIKE '%name%' queries
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_emp_name_trgm
    ON hr.employees USING GIN (
        (lower(first_name) || ' ' || lower(last_name)) gin_trgm_ops
    );

-- =============================================================================
-- Updated_at trigger — auto-update timestamp on row modification
-- (Supplement to JPA @PreUpdate for direct SQL updates / bulk DML)
-- =============================================================================
CREATE OR REPLACE FUNCTION hr.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_employees_updated_at ON hr.employees;
CREATE TRIGGER trg_employees_updated_at
    BEFORE UPDATE ON hr.employees
    FOR EACH ROW
    EXECUTE FUNCTION hr.update_updated_at_column();

-- =============================================================================
-- Audit Log Table — for tracking all changes (MDB writes here asynchronously)
-- =============================================================================
CREATE TABLE IF NOT EXISTS hr.employee_audit_log (
    audit_id      BIGSERIAL     PRIMARY KEY,
    employee_id   BIGINT,
    event_type    VARCHAR(50)   NOT NULL,  -- CREATED, UPDATED, DEACTIVATED
    changed_by    VARCHAR(100),
    changed_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    old_values    JSONB,
    new_values    JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_employee_id
    ON hr.employee_audit_log (employee_id, changed_at DESC);

-- =============================================================================
-- Sample Data (for development / testing)
-- =============================================================================
INSERT INTO hr.employees
    (first_name, last_name, email, department, job_title, salary, hire_date, active, created_by, version)
VALUES
    ('Arjun',   'Sharma',    'arjun.sharma@example.com',   'Engineering', 'Senior Java Developer',     1850000.00, '2018-03-15', TRUE, 'SYSTEM', 0),
    ('Priya',   'Nair',      'priya.nair@example.com',     'Engineering', 'WebSphere Architect',       2400000.00, '2015-07-01', TRUE, 'SYSTEM', 0),
    ('Rahul',   'Verma',     'rahul.verma@example.com',    'Finance',     'Financial Analyst',         1600000.00, '2020-01-10', TRUE, 'SYSTEM', 0),
    ('Sneha',   'Iyer',      'sneha.iyer@example.com',     'HR',          'HR Manager',                1750000.00, '2017-09-20', TRUE, 'SYSTEM', 0),
    ('Vikram',  'Patel',     'vikram.patel@example.com',   'Engineering', 'DevOps Engineer',           1950000.00, '2019-05-12', TRUE, 'SYSTEM', 0),
    ('Ananya',  'Reddy',     'ananya.reddy@example.com',   'Sales',       'Sales Lead',                2100000.00, '2016-11-08', TRUE, 'SYSTEM', 0),
    ('Karthik', 'Menon',     'karthik.menon@example.com',  'Engineering', 'EJB/JPA Specialist',        2050000.00, '2014-04-22', TRUE, 'SYSTEM', 0),
    ('Divya',   'Singh',     'divya.singh@example.com',    'Operations',  'Operations Manager',        1680000.00, '2021-06-30', TRUE, 'SYSTEM', 0),
    ('Rohan',   'Kumar',     'rohan.kumar@example.com',    'Finance',     'CFO',                       4500000.00, '2012-01-15', TRUE, 'SYSTEM', 0),
    ('Meera',   'Pillai',    'meera.pillai@example.com',   'HR',          'Recruiter',                 1200000.00, '2022-08-01', TRUE, 'SYSTEM', 0)
ON CONFLICT (email) DO NOTHING;

-- =============================================================================
-- Useful views for reporting (used by getDepartmentSalarySummary native query)
-- =============================================================================
CREATE OR REPLACE VIEW hr.v_department_summary AS
SELECT
    department,
    COUNT(*)                              AS employee_count,
    ROUND(AVG(salary), 2)                AS avg_salary,
    MAX(salary)                           AS max_salary,
    MIN(salary)                           AS min_salary,
    SUM(salary)                           AS total_payroll
FROM hr.employees
WHERE active = TRUE
GROUP BY department
ORDER BY avg_salary DESC;

COMMENT ON VIEW hr.v_department_summary IS 'Department salary summary view for reporting';

-- Grant view access to wasuser
GRANT SELECT ON hr.v_department_summary TO wasuser;

-- Verify setup
SELECT 'Schema created successfully. Employee count: ' || COUNT(*) AS status
FROM hr.employees;
