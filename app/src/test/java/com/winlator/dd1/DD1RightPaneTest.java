package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1RightPaneTest {
    @Test
    public void signingInComesBeforeTheGameBecauseSavesNeedTheAccount() {
        assertEquals(DD1RightPane.SIGN_IN, DD1RightPane.from(DD1InstallPhase.SIGNED_OUT, true));
        assertEquals(DD1RightPane.SIGN_IN, DD1RightPane.from(DD1InstallPhase.SIGNED_OUT, false));
    }

    @Test
    public void checkingAStoredSessionComesFirstToo() {
        assertEquals(DD1RightPane.CHECKING, DD1RightPane.from(DD1InstallPhase.RESTORING, true));
        assertEquals(DD1RightPane.CHECKING, DD1RightPane.from(DD1InstallPhase.RESTORING, false));
    }

    @Test
    public void onceSignedInAnInstalledGameShowsItsLog() {
        assertEquals(DD1RightPane.LOG, DD1RightPane.from(DD1InstallPhase.READY_TO_INSTALL, true));
        assertEquals(DD1RightPane.LOG, DD1RightPane.from(DD1InstallPhase.READY, true));
        assertEquals(DD1RightPane.LOG, DD1RightPane.from(DD1InstallPhase.ERROR, true));
    }

    @Test
    public void authenticationKeepsTheCodeOnScreenWhileThereIsOne() {
        assertEquals(DD1RightPane.SIGN_IN,
            DD1RightPane.from(DD1InstallPhase.AUTHENTICATING, false, true));
    }

    @Test
    public void waitingForSteamAfterApprovalShowsAWaitNotABlankHalf() {
        assertEquals(DD1RightPane.CHECKING,
            DD1RightPane.from(DD1InstallPhase.AUTHENTICATING, false, false));
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
    public void anErrorIsShownWithTheControlsThatCanRecoverFromIt() {
        assertEquals(DD1RightPane.INSTALL, DD1RightPane.from(DD1InstallPhase.ERROR, false));
    }

    // Adding one DLC to a game that is already installed is still a download,
    // and it was showing the log with Play sitting there enabled: no progress
    // anywhere, and a button that would start the game on top of a half-merged
    // install.
    @Test
    public void aDownloadOutranksAnInstalledGame() {
        assertEquals(DD1RightPane.INSTALL,
            DD1RightPane.from(DD1InstallPhase.DOWNLOADING, true));
        assertEquals(DD1RightPane.INSTALL,
            DD1RightPane.from(DD1InstallPhase.VERIFYING, true));
    }

    // A Steam Guard code is something to answer, so the pane has to be the one
    // with the box in it and not the blank "checking" half-screen.
    @Test
    public void aCodeToTypeIsSomethingToShow() {
        assertEquals(DD1RightPane.SIGN_IN,
            DD1RightPane.from(DD1InstallPhase.AUTHENTICATING, false, true));
        assertEquals(DD1RightPane.SIGN_IN,
            DD1RightPane.from(DD1InstallPhase.AUTHENTICATING, true, true));
    }
}
