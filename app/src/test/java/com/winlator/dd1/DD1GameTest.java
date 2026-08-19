package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class DD1GameTest {
    @Test
    public void findsDarkestExecutableInOwnedGameFiles() throws Exception {
        File filesDir = Files.createTempDirectory("dd1-game").toFile();
        File gameDir = new File(filesDir, "game");
        File executable = createValidGame(gameDir);

        assertEquals(executable, DD1Game.findExecutable(filesDir));
        assertEquals(true, DD1Game.validate(gameDir).valid);
    }

    @Test
    public void returnsNullWhenGameFilesAreMissing() throws Exception {
        File filesDir = Files.createTempDirectory("dd1-missing").toFile();

        assertNull(DD1Game.findExecutable(filesDir));
    }

    @Test
    public void reportsFirstMissingRequiredDirectory() throws Exception {
        File gameDir = Files.createTempDirectory("dd1-invalid").toFile();
        File executable = new File(gameDir, "_windows/win64/Darkest.exe");
        executable.getParentFile().mkdirs();
        writeExecutable(executable);

        assertEquals("audio", DD1Game.validate(gameDir).missingPath);
    }

    private static File createValidGame(File gameDir) throws Exception {
        File executable = new File(gameDir, "_windows/win64/Darkest.exe");
        executable.getParentFile().mkdirs();
        writeExecutable(executable);
        for (String path : new String[]{"audio", "campaign", "dungeons", "heroes", "shared"})
            new File(gameDir, path).mkdirs();
        return executable;
    }

    // Validation reads the PE signature, so fixtures write a real header.
    private static void writeExecutable(File file) throws Exception {
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
            out.write(new byte[] {'M', 'Z', (byte)0x90, 0});
        }
    }
}
