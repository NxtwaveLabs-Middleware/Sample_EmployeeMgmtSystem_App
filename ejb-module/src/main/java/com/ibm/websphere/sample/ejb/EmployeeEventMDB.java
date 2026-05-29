package com.ibm.websphere.sample.ejb;

import com.ibm.websphere.sample.util.AppConstants;

import javax.ejb.*;
import javax.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Message-Driven Bean (MDB) — Async Employee Event Processor.
 *
 * WebSphere Best Practices for MDBs:
 *
 *  1. ACTIVATION SPEC vs LISTENER PORT:
 *     - Modern WebSphere uses JCA Activation Specifications (recommended).
 *       Configure in Admin Console: Resources > JMS > Activation Specifications.
 *     - Legacy Listener Ports are deprecated; migrate to Activation Specs.
 *     - @ActivationConfigProperty maps to the Activation Spec settings.
 *
 *  2. TRANSACTION HANDLING:
 *     - TransactionAttribute.REQUIRED (default for MDB): message consumption
 *       and all EJB calls within onMessage() participate in one XA TX.
 *     - If processing fails and TX rolls back, the message returns to the queue
 *       (redelivery). Configure a Dead Message Queue (DMQ/DLQ) to capture
 *       poison messages after max redelivery attempts.
 *
 *  3. POISON MESSAGE HANDLING:
 *     - Check JMSXDeliveryCount property.
 *     - After threshold, redirect to a DLQ manually or rely on the SIBus/MQ
 *       redelivery threshold configuration.
 *
 *  4. CONCURRENT MDB INSTANCES:
 *     - WebSphere creates a pool of MDB instances; pool size is configured
 *       in the Activation Specification (maxConcurrency).
 *     - Each instance processes one message at a time; all instances share
 *       the same EJB module class loading.
 *
 *  5. ACKNOWLEDGEMENT MODE:
 *     - With CMT (container-managed transactions), the container handles
 *       acknowledgement; do not call message.acknowledge() manually.
 *     - AUTO_ACKNOWLEDGE is used when transaction management is BEAN-managed.
 */
@MessageDriven(
    name = "EmployeeEventMDB",
    activationConfig = {
        // Destination type
        @ActivationConfigProperty(
            propertyName  = "destinationType",
            propertyValue = "javax.jms.Queue"
        ),
        // JNDI name of the queue (resolved by WebSphere Activation Spec)
        @ActivationConfigProperty(
            propertyName  = "destination",
            propertyValue = "jms/EmployeeQueue"
        ),
        // Message selector — only process specific event types
        @ActivationConfigProperty(
            propertyName  = "messageSelector",
            propertyValue = "eventType IN ('CREATED','UPDATED','DEACTIVATED','DEPT_DEACTIVATED')"
        ),
        // Acknowledge mode (used only for BMT)
        @ActivationConfigProperty(
            propertyName  = "acknowledgeMode",
            propertyValue = "Auto-acknowledge"
        ),
        // Max concurrent MDB instances (WebSphere Activation Spec property)
        @ActivationConfigProperty(
            propertyName  = "maxConcurrency",
            propertyValue = "10"
        )
    }
)
@TransactionManagement(TransactionManagementType.CONTAINER)
public class EmployeeEventMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(EmployeeEventMDB.class.getName());

    /** Maximum redelivery count before treating message as poison */
    private static final int MAX_REDELIVERY = 5;

    /**
     * onMessage is called by the WebSphere JMS container when a message
     * arrives on the configured queue.
     *
     * TX boundary:
     *  - Starts automatically before onMessage() is invoked.
     *  - Commits after successful return.
     *  - Rolls back (redelivers message) on exception or setRollbackOnly().
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void onMessage(Message message) {
        try {
            // ---------------------------------------------------------------
            // Poison message guard — avoid infinite redelivery loop
            // ---------------------------------------------------------------
            int deliveryCount = getDeliveryCount(message);
            if (deliveryCount > MAX_REDELIVERY) {
                LOG.severe("Poison message detected (deliveryCount=" + deliveryCount +
                           "). Discarding. JMSMessageID=" + message.getJMSMessageID());
                // Do NOT throw — allow TX to commit so message is consumed.
                // In production, forward to a DLQ here.
                return;
            }

            // ---------------------------------------------------------------
            // Extract event type and dispatch
            // ---------------------------------------------------------------
            String eventType  = message.getStringProperty("eventType");
            long   employeeId = message.propertyExists("employeeId")
                                ? message.getLongProperty("employeeId") : -1L;
            String email      = message.getStringProperty("email");

            LOG.info("MDB processing event=" + eventType +
                     " empId=" + employeeId + " delivery=" + deliveryCount);

            if (message instanceof TextMessage) {
                String body = ((TextMessage) message).getText();
                LOG.fine("Message body: " + body);
            }

            // Dispatch to appropriate handler
            switch (eventType != null ? eventType : "") {
                case "CREATED":
                    handleEmployeeCreated(employeeId, email);
                    break;
                case "UPDATED":
                    handleEmployeeUpdated(employeeId, email);
                    break;
                case "DEACTIVATED":
                    handleEmployeeDeactivated(employeeId);
                    break;
                case "DEPT_DEACTIVATED":
                    handleDeptDeactivated(email); // email holds dept name here
                    break;
                default:
                    LOG.warning("Unknown eventType: " + eventType + " — skipping.");
            }

        } catch (JMSException ex) {
            LOG.log(Level.SEVERE, "JMSException in MDB onMessage — rolling back TX", ex);
            throw new EJBException("JMS processing failure", ex); // triggers TX rollback + redelivery
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unexpected error in MDB onMessage — rolling back TX", ex);
            throw new EJBException("MDB processing failure", ex);
        }
    }

    // =========================================================================
    // Event Handlers
    // =========================================================================

    private void handleEmployeeCreated(long empId, String email) {
        LOG.info("[MDB] Employee CREATED: id=" + empId + ", email=" + email);
        // Examples of async work:
        //  - Send welcome email via JavaMail
        //  - Provision LDAP account
        //  - Notify HRMS downstream system via MQ/REST
        //  - Write to audit database (separate DataSource for audit trail)
    }

    private void handleEmployeeUpdated(long empId, String email) {
        LOG.info("[MDB] Employee UPDATED: id=" + empId);
        // Examples:
        //  - Sync changes to downstream systems
        //  - Invalidate distributed cache entries
    }

    private void handleEmployeeDeactivated(long empId) {
        LOG.info("[MDB] Employee DEACTIVATED: id=" + empId);
        // Examples:
        //  - Revoke access tokens / LDAP group membership
        //  - Archive records to cold storage
    }

    private void handleDeptDeactivated(String department) {
        LOG.info("[MDB] Department DEACTIVATED: " + department);
        // Examples:
        //  - Bulk access revocation
        //  - Department archival process
    }

    // =========================================================================
    // Helper
    // =========================================================================
    private int getDeliveryCount(Message msg) {
        try {
            // JMSXDeliveryCount is a standard JMS property
            return msg.getIntProperty("JMSXDeliveryCount");
        } catch (JMSException ex) {
            return 1; // assume first delivery if unavailable
        }
    }
}
