package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class DlcInstallFilterTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void removesTheDlcTheUserDidNotChoose() throws IOException {
        File game = folder.newFolder("game");
        dlc(game, "580100_crimson_court");
        dlc(game, "735730_color_of_madness");

        DlcInstallFilter.apply(game, Arrays.asList(580100));

        assertEquals(Arrays.asList("580100_crimson_court"), names(game));
    }

    @Test
    public void keepsEverythingWhenEverythingIsChosen() throws IOException {
        File game = folder.newFolder("game");
        dlc(game, "580100_crimson_court");
        dlc(game, "735730_color_of_madness");

        DlcInstallFilter.apply(game, Arrays.asList(580100, 735730));

        assertEquals(2, names(game).size());
    }

    @Test
    public void anEmptyChoiceLeavesTheBaseGameAlone() throws IOException {
        File game = folder.newFolder("game");
        dlc(game, "580100_crimson_court");
        new File(game, "campaign").mkdirs();

        DlcInstallFilter.apply(game, Collections.emptyList());

        assertTrue(new File(game, "campaign").isDirectory());
        assertEquals(Collections.emptyList(), names(game));
    }

    @Test
    public void aFolderWithoutAnIdIsLeftUntouched() throws IOException {
        File game = folder.newFolder("game");
        dlc(game, "readme");

        DlcInstallFilter.apply(game, Collections.emptyList());

        assertEquals(Arrays.asList("readme"), names(game));
    }

    @Test
    public void missingDlcDirectoryIsNotAnError() throws IOException {
        DlcInstallFilter.apply(folder.newFolder("game"), Collections.emptyList());
    }

    private static void dlc(File game, String name) {
        new File(new File(game, "dlc"), name).mkdirs();
    }

    private static java.util.List<String> names(File game) {
        String[] children = new File(game, "dlc").list();
        if (children == null) return Collections.emptyList();
        java.util.List<String> result = Arrays.asList(children);
        java.util.Collections.sort(result);
        return result;
    }
}
