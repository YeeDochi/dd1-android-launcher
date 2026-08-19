package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import com.winlator.dd1.DD1InstallPhase;
import com.winlator.dd1.DD1InstallSnapshot;

import org.junit.Test;

import java.util.Collections;

public class DD1HomeProgressTest {
    @Test
    public void showsThePercentage() {
        assertEquals("50.0%", DD1HomeFragment.progressSummary(snapshot(5000, 10000, 0)));
    }

    @Test
    public void showsNothingUntilTheTotalIsKnown() {
        assertEquals("", DD1HomeFragment.progressSummary(snapshot(0, 0, 0)));
    }

    private static DD1InstallSnapshot snapshot(long downloaded, long total, long speed) {
        return new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING, downloaded, total, speed,
            "", "file", null, Collections.emptyList());
    }
}
