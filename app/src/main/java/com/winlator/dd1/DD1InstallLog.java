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
    private final ArrayDeque<String> visible = new ArrayDeque<>();
    private final StringBuilder full = new StringBuilder();

    public DD1InstallLog(int visibleLimit) {
        if (visibleLimit < 1) throw new IllegalArgumentException("visibleLimit must be positive");
        this.visibleLimit = visibleLimit;
    }

    public synchronized void append(String line) {
        String safe = redact(line == null ? "null" : line);
        full.append(safe).append('\n');
        visible.addLast(safe);
        while (visible.size() > visibleLimit) visible.removeFirst();
    }

    public synchronized List<String> visibleLines() {
        return new ArrayList<>(visible);
    }

    public synchronized String fullText() {
        return full.toString();
    }

    private static String redact(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String marker : SECRET_MARKERS) {
            if (lower.contains(marker)) return "[REDACTED]";
        }
        return line;
    }
}
