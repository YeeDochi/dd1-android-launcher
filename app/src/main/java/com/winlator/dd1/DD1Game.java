package com.winlator.dd1;

import java.io.File;

public abstract class DD1Game {
    private static final String[] EXECUTABLES = {
        "_windowsnosteam/win64/Darkest.exe",
        "_windows/win64/Darkest.exe",
        "_windowsnosteam/win32/Darkest.exe",
        "_windows/win32/Darkest.exe",
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

    // Steam installs these before first launch; the game exits without loading a
    // single DLL when its Visual C++ runtime is missing.
    private static final String[] REDISTRIBUTABLES = {
        "_CommonRedist/vcredist/2013/vcredist_x64.exe",
        "_CommonRedist/vcredist/2022/VC_redist.x64.exe"
    };
    private static final String[] REDIST_MARKERS = {"msvcp120.dll", "msvcp140.dll"};

    public static File pendingRedistributable(File gameDir, File system32Dir) {
        for (int index = 0; index < REDISTRIBUTABLES.length; index++) {
            if (new File(system32Dir, REDIST_MARKERS[index]).isFile()) continue;
            File installer = new File(gameDir, REDISTRIBUTABLES[index]);
            if (installer.isFile()) return installer;
        }
        return null;
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
