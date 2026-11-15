package com.lab.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * In-memory implementation of {@link MaintenanceModeMBean}.
 *
 * <p>Thread-safe. State changes update the change count and last-change
 * timestamp so that a verifier can prove, from the application's own
 * observable state, that an operation was actually invoked.</p>
 */
public final class MaintenanceMode implements MaintenanceModeMBean {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final Object lock = new Object();

    private boolean maintenanceEnabled;
    private volatile String applicationStatus = "OPERATIONAL";
    private volatile long stateChangeCount;
    private volatile String lastStateChange = "never";

    @Override
    public boolean isMaintenanceEnabled() {
        synchronized (lock) {
            return maintenanceEnabled;
        }
    }

    @Override
    public String getApplicationStatus() {
        return applicationStatus;
    }

    @Override
    public String getLastStateChange() {
        return lastStateChange;
    }

    @Override
    public long getStateChangeCount() {
        return stateChangeCount;
    }

    @Override
    public void enableMaintenance() {
        recordChange();
        synchronized (lock) {
            maintenanceEnabled = true;
        }
    }

    @Override
    public void disableMaintenance() {
        recordChange();
        synchronized (lock) {
            maintenanceEnabled = false;
        }
    }

    @Override
    public void setApplicationStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must be non-blank");
        }
        recordChange();
        applicationStatus = status;
    }

    @Override
    public void resetCounters() {
        recordChange();
        stateChangeCount = 0;
        lastStateChange = "reset";
    }

    private void recordChange() {
        synchronized (lock) {
            stateChangeCount++;
            lastStateChange = TS.format(Instant.now());
        }
    }
}
