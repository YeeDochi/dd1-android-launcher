package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class DD1DiagnosticsTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // The runtime's own switches, by the names it reads them under.
    @Test
    public void onTurnsOnTheRuntimesLogsAndPointsThemAtTheFile() {
        File log = new File("/sdcard/DD1/logs/wine-log.txt");
        Map<String, Object> values = DD1Diagnostics.runtimePreferences(true, log);
        assertEquals(true, values.get("enable_wine_debug"));
        assertEquals(1, values.get("box64_logs"));
        assertEquals(true, values.get("save_logs_to_file"));
        assertEquals(log.getPath(), values.get("log_file"));
    }

    // Off has to undo all of it, or the runtime keeps a log window attached and
    // keeps writing.
    @Test
    public void offTurnsEveryOneOfThemBackOff() {
        Map<String, Object> values = DD1Diagnostics.runtimePreferences(false, new File("x"));
        assertEquals(false, values.get("enable_wine_debug"));
        assertEquals(0, values.get("box64_logs"));
        assertEquals(false, values.get("save_logs_to_file"));
        assertTrue(values.containsKey("log_file"));
        assertNull(values.get("log_file"));
    }

    // The runtime empties the log when it starts; the run that went wrong must
    // survive the player opening the game again to look for the file.
    @Test
    public void theLastRunsLogIsKeptOneStepBack() throws IOException {
        File dir = folder.newFolder();
        write(new File(dir, DD1Diagnostics.PREVIOUS), "older");
        write(new File(dir, DD1Diagnostics.LOG), "the stall");
        DD1Diagnostics.keepPrevious(dir, DD1Diagnostics.LOG, DD1Diagnostics.PREVIOUS);
        assertFalse(new File(dir, DD1Diagnostics.LOG).exists());
        assertEquals("the stall", read(new File(dir, DD1Diagnostics.PREVIOUS)));
    }

    // An empty log - the switch was on but the game never got going - is not
    // worth losing the one before it for.
    @Test
    public void anEmptyLogDoesNotPushTheOlderOneOut() throws IOException {
        File dir = folder.newFolder();
        write(new File(dir, DD1Diagnostics.PREVIOUS), "older");
        write(new File(dir, DD1Diagnostics.LOG), "");
        DD1Diagnostics.keepPrevious(dir, DD1Diagnostics.LOG, DD1Diagnostics.PREVIOUS);
        assertEquals("older", read(new File(dir, DD1Diagnostics.PREVIOUS)));
    }

    @Test
    public void theDescriptionIsOneFactPerLineInOrder() {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("gpu", "Adreno (TM) 840");
        facts.put("graphics driver", "turnip,gladio");
        assertEquals("gpu: Adreno (TM) 840\ngraphics driver: turnip,gladio\n",
            DD1Diagnostics.describe(facts));
    }

    private static void write(File file, String text) throws IOException {
        try (FileWriter out = new FileWriter(file)) {
            out.write(text);
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }
}
