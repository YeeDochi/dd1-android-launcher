package com.winlator;

import static org.junit.Assert.assertEquals;

import com.winlator.dd1.DD1InstallPhase;
import com.winlator.dd1.DD1InstallSnapshot;

import org.junit.Test;

import java.util.Collections;

public class DD1HomeProgressTest {
    @Test
    public void showsPercentSizeAndSpeed() {
        assertEquals("50.0% · 2.00 GB / 4.00 GB · 3.0 MB/s",
            DD1HomeFragment.progressSummary(snapshot(2L << 30, 4L << 30, 3L << 20)));
    }

    @Test
    public void omitsPercentWhenTotalUnknown() {
        assertEquals("512 KB", DD1HomeFragment.progressSummary(snapshot(512L << 10, 0, 0)));
    }

    private static DD1InstallSnapshot snapshot(long downloaded, long total, long speed) {
        return new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING, downloaded, total, speed,
            "", "file", null, Collections.emptyList());
    }
}
