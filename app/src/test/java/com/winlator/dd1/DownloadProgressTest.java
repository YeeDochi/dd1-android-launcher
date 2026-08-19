package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadProgressTest {
    @Test
    public void readsAFractionAsAPercentage() {
        assertEquals(42.5, DownloadProgress.percent(0.425f), 0.01);
    }

    @Test
    public void readsAPercentageAsItself() {
        assertEquals(42.5, DownloadProgress.percent(42.5f), 0.01);
    }

    @Test
    public void treatsOneAsComplete() {
        assertEquals(100.0, DownloadProgress.percent(1f), 0.01);
        assertEquals(100.0, DownloadProgress.percent(100f), 0.01);
    }

    @Test
    public void clampsNonsense() {
        assertEquals(0.0, DownloadProgress.percent(-3f), 0.01);
        assertEquals(100.0, DownloadProgress.percent(250f), 0.01);
    }

    @Test
    public void countsFinishedDepotsTowardsTheWhole() {
        DownloadProgress progress = new DownloadProgress();
        progress.onDepotProgress(1, 50f);
        assertEquals(50.0, progress.overall(), 0.01);

        progress.onDepotFinished(1);
        progress.onDepotProgress(2, 50f);
        assertEquals(75.0, progress.overall(), 0.01);
    }

    @Test
    public void neverGoesBackwardsWhenADepotRestarts() {
        DownloadProgress progress = new DownloadProgress();
        progress.onDepotProgress(1, 80f);
        progress.onDepotFinished(1);
        progress.onDepotProgress(2, 5f);

        assertTrue(progress.overall() >= 50.0);
    }

    @Test
    public void staysUnknownUntilTheFirstReport() {
        assertEquals(-1.0, new DownloadProgress().overall(), 0.01);
    }
}
