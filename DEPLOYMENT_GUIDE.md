# WebSphere EAR Application — Setup & Deployment Guide

## Architecture Overview

```
websphere-ear-app.ear
├── lib/
│   ├── shared-lib.jar          ← DTOs, constants (on ALL module classpaths)
│   └── postgresql-42.7.3.jar  ← JDBC driver (bundled in EAR)
├── ejb-module.jar              ← Session Beans, MDB, JPA Entities, DAO
│   ├── EmployeeServiceBean     (Stateless EJB — business façade)
│   ├── ApplicationStartupBean  (Singleton EJB — cache, timers)
│   ├── EmployeeEventMDB        (MDB — async JMS processor)
│   ├── EmployeeDAO             (JPA data access)
│   └── Employee.java           (JPA Entity → hr.employees table)
└── employee-web.war            ← JAX-RS REST API, Filters, Listeners
    ├── GET/POST/PUT/DELETE /employee-web/api/employees
    ├── RequestLoggingFilter
    └── AppContextListener
```

## Technology Stack

| Layer | Technology | WebSphere Provider |
|---|---|---|
| Business Logic | EJB 3.2 Stateless / Singleton / MDB | WAS Container |
| Persistence | JPA 2.2 + Named Queries | OpenJPA (WAS Trad) / EclipseLink (Liberty) |
| Database | PostgreSQL 14+ | JDBC DataSource (XA) |
| REST API | JAX-RS 2.1 | Apache CXF (WAS) |
| Messaging | JMS 2.0 | SIBus (WAS Trad) / MQ RA (Liberty) |
| Transactions | JTA / CMT | WAS Transaction Manager (2PC XA) |
| CDI | CDI 2.0 | WAS CDI Container |
| Security | JAAS / LDAP | WAS Security Domain |

---

## Step 1 — Build the EAR

```bash
# Requires: Java 11+, Maven 3.8+
cd websphere-ear-app
mvn clean package -DskipTests

# Output EAR: ear-project/target/ear-project-1.0.0.ear
```

---

## Step 2 — Create PostgreSQL Database

```bash
# Connect as postgres superuser
psql -U postgres

# Run the DDL script
\i database/01_create_schema.sql

# Verify
\dt hr.*
SELECT COUNT(*) FROM hr.employees;
```

**PostgreSQL JDBC Connection Details (for DataSource config below):**
- URL: `jdbc:postgresql://localhost:5432/employeedb`
- Driver class: `org.postgresql.Driver`
- XA datasource class: `org.postgresql.xa.PGXADataSource`

---

## Step 3 — WebSphere Admin Console Configuration

Access: `https://was-host:9043/ibm/console`

### 3a. Install PostgreSQL JDBC Driver (Shared Library)

> The EAR bundles the driver in `lib/`, but for WebSphere Traditional you
> ALSO register it as a Shared Library so the JDBC Provider can find it.

1. **Environment → Shared Libraries → New**
   - Name: `PostgreSQLJDBC`
   - Class path: `/opt/was/shared/postgresql-42.7.3.jar`
   - (Copy the JAR to this path on the WAS server)

### 3b. Create JDBC Provider

**Resources → JDBC → JDBC Providers → New**

| Field | Value |
|---|---|
| Database type | User-defined |
| Provider name | PostgreSQL JDBC Provider |
| Class path | `/opt/was/shared/postgresql-42.7.3.jar` |
| Implementation class | `org.postgresql.xa.PGXADataSource` |

> **Why XA?** The app uses JMS + JDBC in the same transaction (2PC).
> XA DataSource is required for WebSphere Transaction Manager to coordinate both.

### 3c. Create DataSource

**Resources → JDBC → Data Sources → New** (under the JDBC Provider above)

| Field | Value |
|---|---|
| Data source name | Employee PostgreSQL DS |
| JNDI name | `jdbc/EmployeePostgresDS` |
| Server name | `localhost` |
| Port | `5432` |
| Database name | `employeedb` |
| Component-managed auth alias | `EmployeeDBAlias` |

**Create Auth Alias:**
Security → Global Security → Java Authentication and Authorization Service →
J2C Authentication Data → New:
- Alias: `EmployeeDBAlias`
- User: `wasuser`
- Password: `SecurePass123!`

**Connection Pool Settings** (Data Source → Connection Pool):
```
Maximum connections : 50      ← tune based on PostgreSQL max_connections
Minimum connections : 5
Connection timeout  : 180 sec
Idle timeout        : 1800 sec
Aged timeout        : 7200 sec
Reap time           : 180 sec
Statement cache size: 100     ← caches prepared statements per connection
```

**Test DataSource:** Click "Test Connection" → should show "Test connection succeeded".

### 3d. Create JMS Resources (Service Integration Bus)

#### Create SIBus
**Service Integration → Buses → New:**
- Bus name: `EmployeeBus`
- Add server/cluster member

#### Create SIBus Destinations (Queues)
**Service Integration → Buses → EmployeeBus → Destinations → New:**
- Identifier: `EmployeeSIBQueue`
- Type: Queue

#### Create JMS Connection Factory
**Resources → JMS → Connection Factories → New:**
- Name: `Employee SIB Connection Factory`
- JNDI name: `jms/EmployeeSIBConnectionFactory`
- Bus name: `EmployeeBus`

#### Create JMS Queue
**Resources → JMS → Queues → New:**
- Name: `Employee Queue`
- JNDI name: `jms/EmployeeSIBQueue`
- Bus name: `EmployeeBus`
- Queue name: `EmployeeSIBQueue`

#### Create Activation Specification (for MDB)
**Resources → JMS → Activation Specifications → New:**
- Name: `Employee Activation Spec`
- JNDI name: `jms/EmployeeActivationSpec`
- Destination JNDI: `jms/EmployeeSIBQueue`
- Destination type: Queue
- Maximum concurrent MDB invocations: `10`
- Maximum redelivery count: `5`

---

## Step 4 — Deploy the EAR

**Applications → New Enterprise Application → Upload the EAR**

Deployment wizard steps:
1. **Install options:** Select "Detailed" for full control
2. **Map modules to servers:** Select your WAS server/cluster
3. **Context root:** `/employee-web` (pre-configured in application.xml)
4. **Map resource references:**
   - `jdbc/PostgresDS` → `jdbc/EmployeePostgresDS`
   - `jms/EmployeeQCF` → `jms/EmployeeSIBConnectionFactory`
5. **Map security roles:**
   - `AdminRole` → Group: `HR_Administrators`
   - `UserRole` → Special subject: `All Authenticated Users`
6. **Finish → Save to master configuration**

**Start the application:**
Applications → Enterprise Applications → WebSphere-EAR-Employee-Sample → Start

---

## Step 5 — Verify Deployment

### Check SystemOut.log
```
[INFO] === WebSphere EAR Application Starting ===
[INFO] DataSource OK — Database: PostgreSQL 14.x
[INFO] Department cache warmed up with 5 entries.
[INFO] === Application Startup Complete in 1234 ms ===
```

### Test REST API

```bash
BASE="http://was-host:9080/employee-web/api"
AUTH="admin:password"

# List employees (paginated)
curl -u $AUTH "$BASE/employees?page=1&pageSize=10"

# Get employee by ID
curl -u $AUTH "$BASE/employees/1"

# Search
curl -u $AUTH "$BASE/employees/search?name=arjun&department=Engineering"

# Create employee (Admin role required)
curl -u admin:admin -X POST "$BASE/employees" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Test",
    "lastName": "User",
    "email": "test.user@example.com",
    "department": "Engineering",
    "jobTitle": "Developer",
    "salary": 1500000,
    "hireDate": "2024-01-15"
  }'

# Department salary summary
curl -u $AUTH "$BASE/employees/reports/salary-summary"

# Deactivate employee (soft delete)
curl -u admin:admin -X DELETE "$BASE/employees/1"
```

---

## WebSphere Classloading — Important!

**Application classloading:**
```
Applications → your_app → Class loading and update detection:
  Class loader order: PARENT_LAST  ← app classes take priority
  WAR class loader policy: SINGLE  ← all WARs share one classloader (for EJB access)
```

> **PARENT_LAST** is the WebSphere best practice for EAR applications to avoid
> conflicts between app JARs and WAS-bundled libraries (e.g., your app's
> postgresql driver vs any WAS-bundled driver).

> **SINGLE WAR policy** is required when servlets need to call EJBs in the same
> EAR using `@EJB` injection — they must share the same classloader.

---

## Performance Tuning (Production Checklist)

### JVM Tuning (Servers → JVM → Generic JVM Arguments)
```
-Xms2048m -Xmx4096m
-Xgcpolicy:gencon
-Xmn512m
-XX:+UseCompressedOops
-Dcom.ibm.websphere.ejb.container.discoverInjectedInterfaces=true
```

### Thread Pool (Servers → Thread Pools → WebContainer)
```
Maximum size: 100  (tune to: CPU cores × 2 + pending I/O threads)
Minimum size: 50
```

### Connection Pool (DataSource → Connection Pool)
```
Maximum connections = (threads × avg_concurrent_DB_calls) + headroom
Typical: 50-100 for moderate load
```

### EJB Container (Servers → EJB Container → EJB cache settings)
```
Cache size: 500
Cleanup interval: 3000 ms
```

### WebSphere PMI (Performance Monitoring)
Enable via: **Monitoring and Tuning → Performance Monitoring Infrastructure**
- JDBC connection pool metrics
- EJB method response times
- Thread pool utilization
- JVM heap usage

Use **Tivoli Performance Viewer (TPV)** in the Admin Console for real-time graphs.

---

## Common Issues & Solutions

| Issue | Cause | Solution |
|---|---|---|
| `NamingException: jdbc/PostgresDS not found` | JNDI binding mismatch | Check ibm-ejb-jar-bnd.xml binding-name matches Admin Console JNDI |
| `XAException` on JMS+DB operation | Non-XA DataSource | Use `org.postgresql.xa.PGXADataSource` as implementation class |
| `OptimisticLockException` | Concurrent updates in cluster | Normal behavior; implement retry logic in service layer |
| `EJBException: Transaction rolled back` | DB constraint violation | Catch and wrap with `@ApplicationException` for cleaner error handling |
| Slow startup | No connection pool pre-warming | Set minimum connections > 0; use `@Startup` bean to test DS at deploy |
| MDB not processing messages | Activation Spec not started | Check Activation Spec state in Admin Console → Resources → JMS |
| `ClassNotFoundException: org.postgresql.Driver` | Driver not on classpath | Ensure JDBC JAR is in EAR/lib AND registered in Shared Library |

---

## Project Structure Summary

```
websphere-ear-app/
├── pom.xml                          ← Parent POM (multi-module)
├── shared-lib/                      ← EmployeeDTO, AppConstants
├── ejb-module/
│   └── src/main/
│       ├── java/.../
│       │   ├── entity/Employee.java          ← JPA Entity (@Version, @NamedQuery)
│       │   ├── dao/EmployeeDAO.java           ← JPA DAO (@Stateless, @PersistenceContext)
│       │   ├── ejb/EmployeeServiceBean.java   ← Business EJB (@Stateless, CMT, JMS)
│       │   ├── ejb/ApplicationStartupBean.java← @Singleton @Startup @Schedule
│       │   ├── ejb/EmployeeEventMDB.java      ← @MessageDriven async processor
│       │   └── exception/                     ← @ApplicationException classes
│       └── resources/META-INF/
│           ├── persistence.xml               ← JPA config (OpenJPA + EclipseLink)
│           ├── ejb-jar.xml                   ← TX attrs, security, resource-refs
│           └── ibm-ejb-jar-bnd.xml           ← WebSphere JNDI bindings
├── web-module/
│   └── src/main/
│       ├── java/.../
│       │   ├── servlet/EmployeeRestApplication.java ← JAX-RS @ApplicationPath
│       │   ├── servlet/EmployeeResource.java         ← @Path REST CRUD
│       │   ├── filter/RequestLoggingFilter.java      ← @WebFilter logging
│       │   └── listener/AppContextListener.java      ← @WebListener startup
│       └── webapp/WEB-INF/
│           ├── web.xml                       ← Security, session, error pages
│           └── ibm-web-bnd.xml               ← WAS role/resource bindings
├── ear-project/
│   └── src/main/application/META-INF/
│       ├── application.xml               ← EAR module list, security roles
│       └── ibm-application-bnd.xml       ← EAR-level WAS role mappings
└── database/
    └── 01_create_schema.sql             ← PostgreSQL DDL, indexes, sample data
```
