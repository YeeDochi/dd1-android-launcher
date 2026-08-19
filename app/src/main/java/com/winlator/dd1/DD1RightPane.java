package com.winlator.dd1;

// What the right half of the home screen shows. Signing in does not depend on
// the runtime being unpacked, so it is decided from the Steam state alone.
public enum DD1RightPane {
    CHECKING,
    SIGN_IN,
    INSTALL,
    LOG;

    public static DD1RightPane from(DD1InstallPhase phase, boolean gameInstalled) {
        if (phase == DD1InstallPhase.RESTORING) return CHECKING;
        if (phase == DD1InstallPhase.SIGNED_OUT || phase == DD1InstallPhase.AUTHENTICATING)
            return SIGN_IN;
        if (gameInstalled) return LOG;
        return INSTALL;
    }
}
