package com.lab.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceModeTest {

    private MaintenanceMode bean;

    @BeforeEach
    void setUp() {
        bean = new MaintenanceMode();
    }

    @Test
    void initializesOperational() {
        assertFalse(bean.isMaintenanceEnabled());
        assertEquals("OPERATIONAL", bean.getApplicationStatus());
        assertEquals(0L, bean.getStateChangeCount());
        assertEquals("never", bean.getLastStateChange());
    }

    @Test
    void enableThenDisableTogglesState() {
        bean.enableMaintenance();
        assertTrue(bean.isMaintenanceEnabled());
        bean.disableMaintenance();
        assertFalse(bean.isMaintenanceEnabled());
    }

    @Test
    void eachStateChangeIncrementsCounterAndTimestamps() {
        assertEquals(0L, bean.getStateChangeCount());
        bean.enableMaintenance();
        assertEquals(1L, bean.getStateChangeCount());
        assertNotEquals("never", bean.getLastStateChange(),
                "last change should record a timestamp");
        bean.setApplicationStatus("MAINTENANCE");
        assertEquals(2L, bean.getStateChangeCount());
    }

    @Test
    void resetCountersZeroesState() {
        bean.enableMaintenance();
        bean.setApplicationStatus("DEGRADED");
        assertTrue(bean.getStateChangeCount() > 0);
        bean.resetCounters();
        assertEquals(0L, bean.getStateChangeCount());
        assertEquals("reset", bean.getLastStateChange());
    }

    @Test
    void rejectsBlankStatus() {
        assertThrows(IllegalArgumentException.class, () -> bean.setApplicationStatus(null));
        assertThrows(IllegalArgumentException.class, () -> bean.setApplicationStatus("  "));
    }
}
