package com.winlator.dd1;

import java.io.File;

public abstract class DD1Game {
    private static final String EXECUTABLE = "__build/x64_Debug/Darkest.exe";
    private static final String[] REQUIRED_DIRECTORIES = {
        "audio", "campaign", "dungeons", "heroes", "shared"
    };

    public static File findExecutable(File filesDir) {
        File gameDir = new File(filesDir, "game");
        return validate(gameDir).valid ? new File(gameDir, EXECUTABLE) : null;
    }

    public static Validation validate(File gameDir) {
        File executable = new File(gameDir, EXECUTABLE);
        if (!executable.isFile()) return new Validation(false, EXECUTABLE);
        for (String path : REQUIRED_DIRECTORIES) {
            if (!new File(gameDir, path).isDirectory()) return new Validation(false, path);
        }
        return new Validation(true, null);
    }

    public static final class Validation {
        public final boolean valid;
        public final String missingPath;

        private Validation(boolean valid, String missingPath) {
            this.valid = valid;
            this.missingPath = missingPath;
        }
    }
}
