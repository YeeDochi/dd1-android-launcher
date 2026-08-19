package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DD1SignInControlsTest {
    @Test
    public void checkingASessionCanBeGivenUpOn() {
        DD1SignInControls controls = DD1SignInControls.from(DD1InstallPhase.RESTORING);
        assertFalse(controls.showForm);
        assertTrue("waiting on a stored session must be escapable", controls.showCancel);
        assertFalse(controls.showSignOut);
    }

    @Test
    public void anIdleUserGetsTheSignInForm() {
        DD1SignInControls controls = DD1SignInControls.from(DD1InstallPhase.SIGNED_OUT);
        assertTrue(controls.showForm);
        assertFalse(controls.showCancel);
        assertFalse(controls.showSignOut);
    }

    @Test
    public void awaitingApprovalOffersACancelInsteadOfTheForm() {
        DD1SignInControls controls = DD1SignInControls.from(DD1InstallPhase.AUTHENTICATING);
        assertFalse(controls.showForm);
        assertTrue(controls.showCancel);
        assertFalse("nothing is signed in yet", controls.showSignOut);
    }

    @Test
    public void aSignedInAccountCanBeSignedOut() {
        for (DD1InstallPhase phase : new DD1InstallPhase[] {
                DD1InstallPhase.READY_TO_INSTALL, DD1InstallPhase.NOT_OWNED,
                DD1InstallPhase.DOWNLOADING, DD1InstallPhase.READY}) {
            DD1SignInControls controls = DD1SignInControls.from(phase);
            assertTrue(phase.name(), controls.showSignOut);
            assertFalse(phase.name(), controls.showCancel);
        }
    }

    @Test
    public void anErrorLetsTheUserTryAnotherAccount() {
        DD1SignInControls controls = DD1SignInControls.from(DD1InstallPhase.ERROR);
        assertTrue(controls.showForm);
        assertFalse(controls.showCancel);
    }

    @Test
    public void cancellingIsNotCalledSignOut() {
        assertEquals("Cancel sign-in", DD1SignInControls.CANCEL_LABEL);
    }
}
