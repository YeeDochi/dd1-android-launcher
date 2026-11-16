package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

public class DD1OverridesTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anAddedFileIsLaidDownAndTakenAwayAgain() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "_windows/win64/proxy.dll"), "dll");

        DD1Overrides.Result first = DD1Overrides.apply(overrides, game);
        assertEquals(Collections.singletonList("_windows/win64/proxy.dll"), first.applied);
        assertEquals("dll", read(new File(game, "_windows/win64/proxy.dll")));

        new File(overrides, "_windows/win64/proxy.dll").delete();
        DD1Overrides.Result second = DD1Overrides.apply(overrides, game);
        assertEquals(Collections.singletonList("_windows/win64/proxy.dll"), second.restored);
        assertFalse(new File(game, "_windows/win64/proxy.dll").exists());
    }

    @Test
    public void aReplacedFileGetsItsOriginalBack() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "scripts/hero.lua"), "edited");

        DD1Overrides.apply(overrides, game);
        assertEquals("edited", read(new File(game, "scripts/hero.lua")));

        new File(overrides, "scripts/hero.lua").delete();
        DD1Overrides.apply(overrides, game);
        assertEquals("original", read(new File(game, "scripts/hero.lua")));
    }

    @Test
    public void nothingIsCopiedTwice() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "scripts/hero.lua"), "edited");

        DD1Overrides.apply(overrides, game);
        DD1Overrides.Result again = DD1Overrides.apply(overrides, game);
        assertFalse(again.changed());
    }

    @Test
    public void anEditedOverrideIsLaidDownAgainAndTheOriginalKept() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        File script = new File(overrides, "scripts/hero.lua");
        write(script, "edited");
        DD1Overrides.apply(overrides, game);

        write(script, "edited twice");
        DD1Overrides.Result again = DD1Overrides.apply(overrides, game);
        assertEquals(Collections.singletonList("scripts/hero.lua"), again.applied);
        assertEquals("edited twice", read(new File(game, "scripts/hero.lua")));

        script.delete();
        DD1Overrides.apply(overrides, game);
        assertEquals("original", read(new File(game, "scripts/hero.lua")));
    }

    // A repair puts Steam's file back over the override; Play lays it down again.
    @Test
    public void anOverrideReplacedBehindItsBackIsLaidDownAgain() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "scripts/hero.lua"), "edited");
        DD1Overrides.apply(overrides, game);

        write(new File(game, "scripts/hero.lua"), "steam's own copy");
        DD1Overrides.apply(overrides, game);
        assertEquals("edited", read(new File(game, "scripts/hero.lua")));
    }

    // A repair or an update rewrote the file under the override: that is the
    // game's file now, and the one given back when the override leaves.
    @Test
    public void theFileGivenBackIsTheGamesLatest() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        File script = new File(overrides, "scripts/hero.lua");
        write(script, "edited");
        DD1Overrides.apply(overrides, game);

        write(new File(game, "scripts/hero.lua"), "updated by steam");
        DD1Overrides.apply(overrides, game);
        assertEquals("edited", read(new File(game, "scripts/hero.lua")));

        script.delete();
        DD1Overrides.apply(overrides, game);
        assertEquals("updated by steam", read(new File(game, "scripts/hero.lua")));
    }

    // What a run stopped after the original was moved aside and before the
    // override landed leaves behind: the record says so, and the original
    // still comes back.
    @Test
    public void anOverrideThatNeverLandedStillGivesTheOriginalBack() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        File script = new File(overrides, "scripts/hero.lua");
        write(script, "edited");
        DD1Overrides.apply(overrides, game);
        new File(game, "scripts/hero.lua").delete();

        DD1Overrides.apply(overrides, game);
        assertEquals("edited", read(new File(game, "scripts/hero.lua")));

        script.delete();
        DD1Overrides.apply(overrides, game);
        assertEquals("original", read(new File(game, "scripts/hero.lua")));
    }

    @Test
    public void aLostOriginalIsReportedAndTheOverrideStillLeaves() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        File script = new File(overrides, "scripts/hero.lua");
        write(script, "edited");
        DD1Overrides.apply(overrides, game);
        deleteTree(new File(game, DD1Overrides.STATE + "/originals"));

        script.delete();
        DD1Overrides.Result result = DD1Overrides.apply(overrides, game);
        assertEquals(Collections.singletonList("scripts/hero.lua"), result.lost);
        assertFalse(new File(game, "scripts/hero.lua").exists());
    }

    @Test
    public void aFolderWhereTheGameHasAFileIsSkipped() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "scripts/hero.lua/inner.txt"), "x");
        write(new File(overrides, "proxy.dll"), "dll");

        DD1Overrides.Result result = DD1Overrides.apply(overrides, game);
        assertEquals(Collections.singletonList("scripts/hero.lua/inner.txt"), result.skipped);
        assertEquals(Collections.singletonList("proxy.dll"), result.applied);
        assertEquals("original", read(new File(game, "scripts/hero.lua")));
    }

    @Test
    public void namesFollowTheGamesSpelling() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "SCRIPTS/Hero.LUA"), "edited");

        DD1Overrides.Result result = DD1Overrides.apply(overrides, game);
        assertEquals(Collections.singletonList("scripts/hero.lua"), result.applied);
        assertEquals(Collections.singletonList("hero.lua"),
            Arrays.asList(new File(game, "scripts").list()));
        assertEquals("edited", read(new File(game, "scripts/hero.lua")));
    }

    @Test
    public void anAbsentFolderUndoesWhatWasLaidDown() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "scripts/hero.lua"), "edited");
        DD1Overrides.apply(overrides, game);

        deleteTree(overrides);
        DD1Overrides.apply(overrides, game);
        assertEquals("original", read(new File(game, "scripts/hero.lua")));
    }

    @Test
    public void noFolderAndNoRecordTouchesNothing() throws IOException {
        File game = game();
        DD1Overrides.Result result = DD1Overrides.apply(new File(folder.getRoot(), "none"), game);
        assertFalse(result.changed());
        assertFalse(new File(game, DD1Overrides.STATE).exists());
    }

    @Test
    public void aFileCannotReplaceAFolderOrTheRecord() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, "scripts"), "not a folder");
        write(new File(overrides, DD1Overrides.STATE.toUpperCase() + "/applied.txt"), "x");

        DD1Overrides.Result result = DD1Overrides.apply(overrides, game);
        assertTrue(result.applied.isEmpty());
        assertTrue(new File(game, "scripts").isDirectory());
    }

    @Test
    public void hiddenFilesAreLeftOut() throws IOException {
        File game = game();
        File overrides = folder.newFolder("overrides");
        write(new File(overrides, ".nomedia"), "");
        assertFalse(DD1Overrides.apply(overrides, game).changed());
    }

    private File game() throws IOException {
        File game = folder.newFolder("game");
        write(new File(game, "_windows/win64/Darkest.exe"), "MZ");
        write(new File(game, "scripts/hero.lua"), "original");
        return game;
    }

    private static void write(File file, String text) throws IOException {
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
        }
        // Two writes inside one clock tick must still read as a change.
        file.setLastModified(System.currentTimeMillis() + text.length() * 1000L);
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), "UTF-8");
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
