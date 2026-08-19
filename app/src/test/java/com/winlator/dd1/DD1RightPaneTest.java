package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1RightPaneTest {
    @Test
    public void signInComesFirstEvenWhileTheRuntimeIsStillUnpacking() {
        assertEquals(DD1RightPane.SIGN_IN,
            DD1RightPane.from(DD1InstallPhase.SIGNED_OUT, false));
        assertEquals(DD1RightPane.SIGN_IN,
            DD1RightPane.from(DD1InstallPhase.SIGNED_OUT, true));
    }

    @Test
    public void authenticationKeepsTheSignInFormOnScreen() {
        assertEquals(DD1RightPane.SIGN_IN,
            DD1RightPane.from(DD1InstallPhase.AUTHENTICATING, false));
    }

    @Test
    public void ownedButUninstalledShowsTheDownloadControls() {
        assertEquals(DD1RightPane.INSTALL,
            DD1RightPane.from(DD1InstallPhase.READY_TO_INSTALL, false));
        assertEquals(DD1RightPane.INSTALL,
            DD1RightPane.from(DD1InstallPhase.DOWNLOADING, false));
        assertEquals(DD1RightPane.INSTALL,
            DD1RightPane.from(DD1InstallPhase.NOT_OWNED, false));
    }

    @Test
    public void anInstalledGameLeavesTheLogInPlace() {
        assertEquals(DD1RightPane.LOG, DD1RightPane.from(DD1InstallPhase.READY, true));
        assertEquals(DD1RightPane.LOG, DD1RightPane.from(DD1InstallPhase.VERIFYING, true));
    }

    @Test
    public void anErrorIsShownWithTheControlsThatCanRecoverFromIt() {
        assertEquals(DD1RightPane.INSTALL, DD1RightPane.from(DD1InstallPhase.ERROR, false));
        assertEquals(DD1RightPane.LOG, DD1RightPane.from(DD1InstallPhase.ERROR, true));
    }
}
