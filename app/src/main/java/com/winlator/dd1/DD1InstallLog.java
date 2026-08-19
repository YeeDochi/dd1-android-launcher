package com.winlator.dd1;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DD1InstallLog {
    private static final String[] SECRET_MARKERS = {
        "authorization", "password", "refresh_token", "access_token", "cookie"
    };

    private final int visibleLimit;
    // Only the visible tail is kept. A full transcript grew a line per file and
    // a 4 GB download names hundreds of thousands of them.
    private final ArrayDeque<String> visible = new ArrayDeque<>();

    public DD1InstallLog(int visibleLimit) {
        if (visibleLimit < 1) throw new IllegalArgumentException("visibleLimit must be positive");
        this.visibleLimit = visibleLimit;
    }

    public synchronized void append(String line) {
        String safe = redact(line == null ? "null" : line);
        visible.addLast(safe);
        while (visible.size() > visibleLimit) visible.removeFirst();
    }

    public synchronized List<String> visibleLines() {
        return new ArrayList<>(visible);
    }

    private static String redact(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String marker : SECRET_MARKERS) {
            if (lower.contains(marker)) return "[REDACTED]";
        }
        return line;
    }
}
