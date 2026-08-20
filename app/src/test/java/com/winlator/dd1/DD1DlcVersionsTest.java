package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class DD1DlcVersionsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    // The record is what tells an installed DLC apart from an out-of-date one,
    // so it has to survive being written and read back.
    @Test
    public void remembersTheVersionThatWasInstalled() throws IOException {
        File files = folder.newFolder();

        DD1DlcVersions.record(files, 580100, "3379207342614393360");

        assertEquals("3379207342614393360", DD1DlcVersions.installed(files).get(580100));
    }

    @Test
    public void knowsNothingBeforeAnythingIsWritten() throws IOException {
        assertNull(DD1DlcVersions.installed(folder.newFolder()).get(580100));
    }

    @Test
    public void aSecondDlcDoesNotDisplaceTheFirst() throws IOException {
        File files = folder.newFolder();

        DD1DlcVersions.record(files, 580100, "aaa");
        DD1DlcVersions.record(files, 735730, "bbb");

        assertEquals("aaa", DD1DlcVersions.installed(files).get(580100));
        assertEquals("bbb", DD1DlcVersions.installed(files).get(735730));
    }

    @Test
    public void anUpdateReplacesTheVersionRatherThanAddingOne() throws IOException {
        File files = folder.newFolder();

        DD1DlcVersions.record(files, 580100, "old");
        DD1DlcVersions.record(files, 580100, "new");

        assertEquals("new", DD1DlcVersions.installed(files).get(580100));
        assertEquals(1, DD1DlcVersions.installed(files).size());
    }

    // Removing content and leaving its version behind would report an install
    // that is not there.
    @Test
    public void forgettingADlcDropsOnlyItsLine() throws IOException {
        File files = folder.newFolder();
        DD1DlcVersions.record(files, 580100, "aaa");
        DD1DlcVersions.record(files, 735730, "bbb");

        DD1DlcVersions.forget(files, Collections.singletonList(580100));

        assertNull(DD1DlcVersions.installed(files).get(580100));
        assertEquals("bbb", DD1DlcVersions.installed(files).get(735730));
    }

    // A DLC installed before this record existed has no line, and calling that an
    // update would offer every owner a fresh download of content they already
    // have. What the launcher put on disk was the version Steam was offering at
    // the time, so that is what is adopted; if Steam has moved since, the next
    // change to the manifest says so.
    @Test
    public void adoptsTheCurrentVersionForContentInstalledBeforeTheRecord() throws IOException {
        File files = folder.newFolder();
        DD1DepotCatalog catalog = DD1DepotCatalog.of(Arrays.asList(
            new DD1DepotCatalog.Row(580100, 580100, "windows", "current"),
            new DD1DepotCatalog.Row(735730, 735730, "windows", "also-current")));

        DD1DlcVersions.adopt(files, Arrays.asList(580100, 735730), catalog);

        assertEquals("current", DD1DlcVersions.installed(files).get(580100));
        assertEquals("also-current", DD1DlcVersions.installed(files).get(735730));
    }

    @Test
    public void adoptingLeavesAVersionItAlreadyKnowsAlone() throws IOException {
        File files = folder.newFolder();
        DD1DlcVersions.record(files, 580100, "installed-long-ago");
        DD1DepotCatalog catalog = DD1DepotCatalog.of(Collections.singletonList(
            new DD1DepotCatalog.Row(580100, 580100, "windows", "current")));

        DD1DlcVersions.adopt(files, Collections.singletonList(580100), catalog);

        assertEquals("installed-long-ago", DD1DlcVersions.installed(files).get(580100));
    }

    @Test
    public void adoptingClaimsNothingWhenTheCatalogueIsUnknown() throws IOException {
        File files = folder.newFolder();

        DD1DlcVersions.adopt(files, Collections.singletonList(580100), DD1DepotCatalog.empty());

        assertNull(DD1DlcVersions.installed(files).get(580100));
    }

    @Test
    public void aFileWithRubbishInItIsReadForWhatItHas() throws IOException {
        File files = folder.newFolder();
        try (java.io.FileWriter writer = new java.io.FileWriter(new File(files, "dlc-versions"))) {
            writer.write("nonsense\n580100=aaa\n\n=bbb\n");
        }

        assertEquals(Arrays.asList(580100),
            new java.util.ArrayList<>(DD1DlcVersions.installed(files).keySet()));
    }
}
