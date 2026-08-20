package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DD1InstallPhaseTest {
    // A license list resent mid-download ends on READY_TO_INSTALL, which the
    // service reads as idle: taking it stopped the service and killed the
    // download.
    @Test
    public void ownershipNewsDoesNotInterruptADownload() {
        assertFalse(DD1InstallPhase.READY_TO_INSTALL.interruptsDownload());
        assertFalse(DD1InstallPhase.AUTHENTICATING.interruptsDownload());
        assertFalse(DD1InstallPhase.NOT_OWNED.interruptsDownload());
        assertFalse(DD1InstallPhase.RESTORING.interruptsDownload());
    }

    @Test
    public void losingTheSessionDoes() {
        assertTrue(DD1InstallPhase.SIGNED_OUT.interruptsDownload());
        assertTrue(DD1InstallPhase.ERROR.interruptsDownload());
    }
}
