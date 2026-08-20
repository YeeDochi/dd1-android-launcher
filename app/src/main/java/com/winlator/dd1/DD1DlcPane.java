package com.winlator.dd1;

// What the content screen shows before it shows a list. Waiting on Steam and
// having nothing to show are different things, and an empty list means the first
// one whenever the account is still on its way in.
public enum DD1DlcPane {
    LOADING,
    EMPTY,
    LIST;

    public static DD1DlcPane from(DD1InstallPhase phase, boolean ownsAnything) {
        if (ownsAnything) return LIST;
        if (phase == DD1InstallPhase.RESTORING || phase == DD1InstallPhase.AUTHENTICATING)
            return LOADING;
        return EMPTY;
    }
}
