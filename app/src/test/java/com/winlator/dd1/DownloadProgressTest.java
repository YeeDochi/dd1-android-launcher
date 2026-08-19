package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

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
    public void clampsNonsense() {
        assertEquals(0.0, DownloadProgress.percent(-3f), 0.01);
        assertEquals(100.0, DownloadProgress.percent(250f), 0.01);
    }

    @Test
    public void startsWithNothingToShow() {
        DownloadProgress progress = new DownloadProgress();

        assertEquals(-1.0, progress.currentPercent(), 0.01);
        assertEquals(0, progress.currentDepot());
        assertEquals(0, progress.finishedCount());
    }

    @Test
    public void tracksTheDepotBeingFetched() {
        DownloadProgress progress = new DownloadProgress();
        progress.onDepotProgress(228985, 30f);

        assertEquals(228985, progress.currentDepot());
        assertEquals(30.0, progress.currentPercent(), 0.01);
    }

    @Test
    public void movingToAnotherDepotStartsItsFigureOver() {
        DownloadProgress progress = new DownloadProgress();
        progress.onDepotProgress(1, 90f);
        progress.onDepotFinished(1);
        progress.onDepotProgress(2, 0f);

        assertEquals(2, progress.currentDepot());
        assertEquals("a fresh depot has not reported yet", -1.0, progress.currentPercent(), 0.01);
        assertEquals(1, progress.finishedCount());
    }

    @Test
    public void theManifestLinesSettleThePartTotalBeforeAnyContentArrives() {
        DownloadProgress progress = new DownloadProgress();
        progress.onStatus("Downloading manifest for depot 228985");
        progress.onStatus("Downloading manifest for depot 228989");
        progress.onStatus("Allocating file: game.exe");

        assertEquals("1/2", progress.part());
        progress.onDepotFinished(228985);
        assertEquals("2/2", progress.part());
    }

    @Test
    public void thePartIsWhereTheDepotInHandSitsNotHowManyAreDone() {
        DownloadProgress progress = new DownloadProgress();
        for (int depotId : new int[]{1, 2, 3, 4}) {
            progress.onStatus("Downloading manifest for depot " + depotId);
        }
        // A resumed download validates what it already has and finishes those
        // parts at once; the figure must still name the part being fetched.
        progress.onDepotFinished(1);
        progress.onDepotFinished(2);
        progress.onDepotProgress(3, 5f);

        assertEquals("3/4", progress.part());
    }

    @Test
    public void walkingOverAlreadyFetchedDepotsDoesNotMoveTheFigure() {
        DownloadProgress progress = new DownloadProgress();
        for (int depotId : new int[]{1, 2, 3}) {
            progress.onStatus("Downloading manifest for depot " + depotId);
        }
        progress.onDepotProgress(1, 40f);
        // The resume sweep finishes the parts already on disk.
        progress.onDepotSeen(2);
        progress.onDepotFinished(2);
        progress.onDepotSeen(3);
        progress.onDepotFinished(3);

        assertEquals("1/3", progress.part());
        assertEquals("the depot in hand still has its figure", 40.0,
            progress.currentPercent(), 0.01);
    }

    @Test
    public void thePartNumberNeverRunsPastTheTotal() {
        DownloadProgress progress = new DownloadProgress();
        progress.onStatus("Downloading manifest for depot 7");
        progress.onDepotFinished(7);

        assertEquals("1/1", progress.part());
    }

    @Test
    public void withoutManifestLinesItCountsWhatItHasSeen() {
        DownloadProgress progress = new DownloadProgress();
        progress.onDepotProgress(7, 10f);

        assertEquals("1/1", progress.part());
    }

    @Test
    public void aCallbackWithoutADepotKeepsTheCurrentOne() {
        DownloadProgress progress = new DownloadProgress();
        progress.onDepotProgress(7, 10f);
        progress.onDepotProgress(0, 50f);

        assertEquals(7, progress.currentDepot());
        assertEquals(50.0, progress.currentPercent(), 0.01);
    }
}
