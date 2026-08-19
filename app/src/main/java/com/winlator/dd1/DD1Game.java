package com.winlator.dd1;

import java.io.File;

public abstract class DD1Game {
    public static File findExecutable(File filesDir) {
        File executable = new File(filesDir, "game/__build/x64_Debug/Darkest.exe");
        return executable.isFile() ? executable : null;
    }
}
