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
        File executable = new File(filesDir, "game/__build/x64_Debug/Darkest.exe");
        executable.getParentFile().mkdirs();
        executable.createNewFile();

        assertEquals(executable, DD1Game.findExecutable(filesDir));
    }

    @Test
    public void returnsNullWhenGameFilesAreMissing() throws Exception {
        File filesDir = Files.createTempDirectory("dd1-missing").toFile();

        assertNull(DD1Game.findExecutable(filesDir));
    }
}
