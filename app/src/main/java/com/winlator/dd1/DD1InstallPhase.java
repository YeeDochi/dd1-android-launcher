package com.winlator.dd1;

public enum DD1InstallPhase {
    RESTORING,
    SIGNED_OUT,
    AUTHENTICATING,
    NOT_OWNED,
    READY_TO_INSTALL,
    DOWNLOADING,
    VERIFYING,
    READY,
    ERROR;

    // Steam talks about the account while the download runs: it resends its
    // license list, the ownership sweep follows, and the phase it ends on is one
    // the service reads as idle. Only news that genuinely ends the session may
    // interrupt bytes that are moving.
    public boolean interruptsDownload() {
        return this == SIGNED_OUT || this == ERROR;
    }
}
