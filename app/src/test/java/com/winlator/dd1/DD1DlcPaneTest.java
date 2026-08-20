package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1DlcPaneTest {
    // A stored token means the account is on its way in, and the list is empty
    // because nothing has answered yet. Claiming "no DLC found" then is wrong,
    // and it is the same mistake as reading an unread cloud as an empty one.
    @Test
    public void anEmptyListWhileSigningInIsAWait() {
        assertEquals(DD1DlcPane.LOADING, DD1DlcPane.from(DD1InstallPhase.RESTORING, false));
        assertEquals(DD1DlcPane.LOADING,
            DD1DlcPane.from(DD1InstallPhase.AUTHENTICATING, false));
    }

    @Test
    public void anEmptyListOnceSignedInIsAnEmptyList() {
        assertEquals(DD1DlcPane.EMPTY,
            DD1DlcPane.from(DD1InstallPhase.READY_TO_INSTALL, false));
        assertEquals(DD1DlcPane.EMPTY, DD1DlcPane.from(DD1InstallPhase.READY, false));
    }

    @Test
    public void beingSignedOutIsNotAWait() {
        assertEquals(DD1DlcPane.EMPTY, DD1DlcPane.from(DD1InstallPhase.SIGNED_OUT, false));
    }

    // Owning something settles it whatever Steam is doing: the rows are already
    // on screen and replacing them with a spinner would be a flicker.
    @Test
    public void owningSomethingIsAlwaysAList() {
        assertEquals(DD1DlcPane.LIST, DD1DlcPane.from(DD1InstallPhase.RESTORING, true));
        assertEquals(DD1DlcPane.LIST, DD1DlcPane.from(DD1InstallPhase.READY, true));
    }
}
