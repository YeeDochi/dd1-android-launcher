package com.winlator.dd1;

import com.winlator.R;

import java.util.List;
import java.util.Map;

public final class DD1SteamEvents {
    public static final int APP_ID = 262060;

    // Resolved through a lambda rather than a Context so the state machine
    // stays a plain object that tests can drive.
    public interface Texts {
        String get(int id);
    }

    private final Texts texts;
    private final DD1InstallLog log = new DD1InstallLog(1000);
    private DD1InstallSnapshot snapshot = DD1InstallSnapshot.restoring();
    private List<Integer> ownedDlc = java.util.Collections.emptyList();

    public DD1SteamEvents(Texts texts) {
        this.texts = texts;
    }

    private String text(int id) {
        return texts.get(id);
    }

    public synchronized DD1InstallSnapshot authStarted(String challengeUrl) {
        log.append("Steam QR authentication started");
        return update(DD1InstallPhase.AUTHENTICATING, text(R.string.dd1_state_approve), challengeUrl);
    }

    public synchronized DD1InstallSnapshot loggedOn() {
        log.append("Steam login approved; checking ownership");
        return update(DD1InstallPhase.AUTHENTICATING, text(R.string.dd1_state_ownership), null);
    }

    public synchronized DD1InstallSnapshot checkingLicenses(int count) {
        log.append("Reading " + count + " Steam licenses");
        return update(DD1InstallPhase.AUTHENTICATING, text(R.string.dd1_state_ownership), null);
    }

    public synchronized DD1InstallSnapshot packagesResolved(Map<Integer, List<Integer>> packageApps,
            long elapsedMillis, List<Integer> dlcAppIds) {
        log.append("Ownership check took " + (elapsedMillis / 1000) + "s");
        boolean owned = DD1Ownership.ownsApp(packageApps, APP_ID);
        ownedDlc = DD1Ownership.ownedAppIds(packageApps);
        log.append(owned ? "Darkest Dungeon ownership verified" : "Darkest Dungeon is not owned");
        if (owned) log.append("Owned DLC: "
            + DlcSelection.parse(null, ownedDlc, dlcAppIds).owned().size()
            + " of " + dlcAppIds.size());
        return update(owned ? DD1InstallPhase.READY_TO_INSTALL : DD1InstallPhase.NOT_OWNED,
            text(owned ? R.string.dd1_state_ready_to_install : R.string.dd1_state_not_owned), null);
    }

    public synchronized DD1InstallSnapshot failed(String detail) {
        log.append(detail);
        return update(DD1InstallPhase.ERROR, text(R.string.dd1_state_failed), null);
    }

    public synchronized DD1InstallSnapshot sessionExpired() {
        log.append("Stored Steam session did not answer; signing in again is required");
        return update(DD1InstallPhase.SIGNED_OUT, text(R.string.dd1_state_sign_in), null);
    }

    public synchronized DD1InstallSnapshot signedOut() {
        log.append("Steam session closed");
        return update(DD1InstallPhase.SIGNED_OUT, text(R.string.dd1_state_sign_in), null);
    }

    public synchronized List<Integer> ownedDlc() {
        return ownedDlc;
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
