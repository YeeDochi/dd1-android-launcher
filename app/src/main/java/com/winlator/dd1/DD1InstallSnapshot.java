package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DD1InstallSnapshot {
    public final DD1InstallPhase phase;
    public final long downloadedBytes;
    public final long totalBytes;
    public final long bytesPerSecond;
    public final String message;
    public final String currentFile;
    public final String challengeUrl;
    public final List<String> logLines;

    public DD1InstallSnapshot(DD1InstallPhase phase, long downloadedBytes,
            long totalBytes, long bytesPerSecond, String message,
            String currentFile, String challengeUrl, List<String> logLines) {
        this.phase = phase;
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.bytesPerSecond = bytesPerSecond;
        this.message = message;
        this.currentFile = currentFile;
        this.challengeUrl = challengeUrl;
        this.logLines = Collections.unmodifiableList(new ArrayList<>(logLines));
    }

    public static DD1InstallSnapshot signedOut() {
        return new DD1InstallSnapshot(DD1InstallPhase.SIGNED_OUT, 0, 0, 0,
            "Steam sign-in required", null, null, Collections.emptyList());
    }
}
