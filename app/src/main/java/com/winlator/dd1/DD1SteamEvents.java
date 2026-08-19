package com.winlator.dd1;

import java.util.List;
import java.util.Map;

public final class DD1SteamEvents {
    public static final int APP_ID = 262060;

    private final DD1InstallLog log = new DD1InstallLog(1000);
    private DD1InstallSnapshot snapshot = DD1InstallSnapshot.signedOut();

    public synchronized DD1InstallSnapshot authStarted(String challengeUrl) {
        log.append("Steam QR authentication started");
        return update(DD1InstallPhase.AUTHENTICATING, "Approve sign-in with Steam Mobile", challengeUrl);
    }

    public synchronized DD1InstallSnapshot loggedOn() {
        log.append("Steam login approved; checking ownership");
        return update(DD1InstallPhase.AUTHENTICATING, "Checking Darkest Dungeon ownership", null);
    }

    public synchronized DD1InstallSnapshot packagesResolved(Map<Integer, List<Integer>> packageApps) {
        boolean owned = DD1Ownership.ownsApp(packageApps, APP_ID);
        log.append(owned ? "Darkest Dungeon ownership verified" : "Darkest Dungeon is not owned");
        return update(owned ? DD1InstallPhase.READY_TO_INSTALL : DD1InstallPhase.NOT_OWNED,
            owned ? "Ready to download owned game and DLC" : "Darkest Dungeon is not owned by this account", null);
    }

    public synchronized DD1InstallSnapshot failed(String detail) {
        log.append(detail);
        return update(DD1InstallPhase.ERROR, "Steam operation failed", null);
    }

    public synchronized DD1InstallSnapshot signedOut() {
        log.append("Steam session closed");
        return update(DD1InstallPhase.SIGNED_OUT, "Steam sign-in required", null);
    }

    public synchronized DD1InstallSnapshot snapshot() {
        return snapshot;
    }

    private DD1InstallSnapshot update(DD1InstallPhase phase, String message, String challengeUrl) {
        snapshot = new DD1InstallSnapshot(phase, 0, 0, 0, message, null,
            challengeUrl, log.visibleLines());
        return snapshot;
    }
}
