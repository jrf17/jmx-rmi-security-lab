package com.lab.application;

/**
 * Management interface for the synthetic {@code MaintenanceMode} resource.
 *
 * <p>Every operation is deliberately harmless: state is held in memory only,
 * no process is spawned, and no file or network resource is touched beyond the
 * JMX/RMI transport itself. The resource exists purely to give the laboratory
 * an observable, stateful target whose access can be demonstrated and whose
 * controls can be verified.</p>
 */
public interface MaintenanceModeMBean {

    /** @return true when synthetic maintenance mode is active. */
    boolean isMaintenanceEnabled();

    /** @return synthetic application status string. */
    String getApplicationStatus();

    /** @return timestamp of the last state-changing operation, or "never". */
    String getLastStateChange();

    /** @return number of state-changing operations performed since startup or reset. */
    long getStateChangeCount();

    /** Activate synthetic maintenance mode. */
    void enableMaintenance();

    /** Deactivate synthetic maintenance mode. */
    void disableMaintenance();

    /**
     * Set the synthetic application status.
     *
     * @param status arbitrary non-null, non-empty string.
     * @throws IllegalArgumentException if {@code status} is null or blank.
     */
    void setApplicationStatus(String status);

    /** Reset the laboratory counters and last-change timestamp. */
    void resetCounters();
}
