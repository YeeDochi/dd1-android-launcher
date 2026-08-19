package com.winlator.dd1;

import java.io.File;

public abstract class DD1Game {
    private static final String[] EXECUTABLES = {
        "_windowsnosteam/win64/Darkest.exe",
        "_windows/win64/Darkest.exe",
        "_windowsnosteam/Darkest.exe",
        "_windows/Darkest.exe"
    };
    private static final String[] REQUIRED_DIRECTORIES = {
        "audio", "campaign", "dungeons", "heroes", "shared"
    };

    public static File findExecutable(File filesDir) {
        File gameDir = new File(filesDir, "game");
        return validate(gameDir).valid ? executable(gameDir) : null;
    }

    public static Validation validate(File gameDir) {
        if (executable(gameDir) == null) return new Validation(false, EXECUTABLES[1]);
        for (String path : REQUIRED_DIRECTORIES) {
            if (!new File(gameDir, path).isDirectory()) return new Validation(false, path);
        }
        return new Validation(true, null);
    }

    private static File executable(File gameDir) {
        for (String path : EXECUTABLES) {
            File executable = new File(gameDir, path);
            if (executable.isFile()) return executable;
        }
        return null;
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
