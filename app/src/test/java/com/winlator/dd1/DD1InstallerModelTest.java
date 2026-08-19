package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class DD1InstallerModelTest {
    @Test
    public void redactsSecretsAndBoundsVisibleLog() {
        DD1InstallLog log = new DD1InstallLog(2);

        log.append("Authorization: Bearer abc");
        log.append("file one");
        log.append("file two");

        assertEquals(Arrays.asList("file one", "file two"), log.visibleLines());
        assertFalse(log.visibleLines().toString().contains("abc"));
        assertFalse(log.visibleLines().toString().contains("Authorization"));
    }

    @Test
    public void snapshotCopiesVisibleLogLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(Collections.singletonList("one"));
        DD1InstallSnapshot snapshot = new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING,
            10, 20, 5, "Downloading", "file", null, lines);
        lines.add("two");

        assertEquals(Collections.singletonList("one"), snapshot.logLines);
        try {
            snapshot.logLines.add("three");
            fail("snapshot log must be immutable");
        }
        catch (UnsupportedOperationException expected) {}
    }
}
