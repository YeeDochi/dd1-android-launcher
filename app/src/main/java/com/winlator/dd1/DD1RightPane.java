package com.winlator.dd1;

// What the right half of the home screen shows. Signing in does not depend on
// the runtime being unpacked, so it is decided from the Steam state alone.
public enum DD1RightPane {
    CHECKING,
    SIGN_IN,
    INSTALL,
    LOG;

    // The account comes first even when the game is installed, because save
    // synchronisation needs it. Only once signed in does the install decide.
    public static DD1RightPane from(DD1InstallPhase phase, boolean gameInstalled) {
        return from(phase, gameInstalled, false);
    }

    // Signing in only has something to show while there is a code to scan or a
    // form to fill; the wait after that is a wait, not a blank half-screen.
    public static DD1RightPane from(DD1InstallPhase phase, boolean gameInstalled,
            boolean hasChallenge) {
        if (phase == DD1InstallPhase.RESTORING) return CHECKING;
        if (phase == DD1InstallPhase.AUTHENTICATING) return hasChallenge ? SIGN_IN : CHECKING;
        if (phase == DD1InstallPhase.SIGNED_OUT) return SIGN_IN;
        if (gameInstalled) return LOG;
        return INSTALL;
    }
}
