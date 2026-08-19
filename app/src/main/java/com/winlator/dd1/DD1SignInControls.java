package com.winlator.dd1;

// Which sign-in controls belong on screen. Waiting for Steam Mobile approval can
// last minutes, so there is always a way out that is not called "sign out" -
// nothing is signed in yet at that point.
public final class DD1SignInControls {
    public static final String CANCEL_LABEL = "Cancel sign-in";

    public final boolean showForm;
    public final boolean showCancel;
    public final boolean showSignOut;

    private DD1SignInControls(boolean showForm, boolean showCancel, boolean showSignOut) {
        this.showForm = showForm;
        this.showCancel = showCancel;
        this.showSignOut = showSignOut;
    }

    public static DD1SignInControls from(DD1InstallPhase phase) {
        if (phase == DD1InstallPhase.AUTHENTICATING) return new DD1SignInControls(false, true, false);
        if (phase == DD1InstallPhase.SIGNED_OUT || phase == DD1InstallPhase.ERROR)
            return new DD1SignInControls(true, false, false);
        return new DD1SignInControls(false, false, true);
    }
}
