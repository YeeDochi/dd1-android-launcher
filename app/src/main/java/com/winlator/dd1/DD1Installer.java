package com.winlator.dd1;

import java.io.File;

public final class DD1Installer {
    private DD1Installer() {}

    public static Result activate(File filesDir) {
        File stagingRoot = new File(filesDir, "staging");
        File staging = new File(stagingRoot, "game");
        DD1Game.Validation validation = DD1Game.validate(staging);
        if (!validation.valid) return new Result(false, "Missing " + validation.missingPath);

        File active = new File(filesDir, "game");
        File previous = new File(stagingRoot, "previous-game");
        delete(previous);
        if (active.exists() && !active.renameTo(previous))
            return new Result(false, "Unable to preserve installed game");
        if (!staging.renameTo(active)) {
            if (previous.exists()) previous.renameTo(active);
            return new Result(false, "Unable to activate downloaded game");
        }
        delete(previous);
        return new Result(true, null);
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (file.exists()) file.delete();
    }

    public static final class Result {
        public final boolean success;
        public final String error;

        private Result(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }
}
