package com.winlator.dd1;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Where the game keeps its progress, and whether what is there is progress at
// all. The path is inside the Wine prefix rather than beside the game, because
// that is where Wine puts the user's Documents directory and where the game
// looks. Replacing the game leaves it alone; resetting the prefix would not,
// which is why that is not offered.
public final class DD1Saves {
    private DD1Saves() {}

    private static final String PREFIX_PATH =
        "rootfs/home/xuser-1/.wine/drive_c/users/xuser/Documents/Darkest";

    public static File root(File filesDir) {
        return new File(filesDir, PREFIX_PATH);
    }

    // The same place, reached from a profile directory rather than from files/ -
    // the launcher only ever uses xuser-1, but a profile left behind by an
    // interrupted one can carry any number.
    public static File saveTreeIn(File profileDir) {
        return new File(profileDir, PREFIX_PATH.substring(
            PREFIX_PATH.indexOf("xuser-1/") + "xuser-1/".length()));
    }

    public static boolean isSaveTree(File root) {
        return !profiles(root).isEmpty();
    }

    // A slot the player has never used is an empty directory, and the game
    // writes persist.game.json into one it has, so that file is what makes a
    // slot a save.
    public static List<File> profiles(File root) {
        List<File> found = new ArrayList<>();
        File[] entries = root.listFiles();
        if (entries == null) return found;
        for (File entry : entries) {
            if (slotOf(entry.getName()) < 0) continue;
            File game = new File(entry, "persist.game.json");
            if (game.isFile() && game.length() > 0) found.add(entry);
        }
        found.sort((left, right) ->
            Integer.compare(slotOf(left.getName()), slotOf(right.getName())));
        return found;
    }

    // "profile_10" is a slot, and it sorts after "profile_2" rather than
    // between one and three; anything else in the directory is not a slot.
    static int slotOf(String name) {
        if (!name.startsWith("profile_")) return -1;
        try {
            return Integer.parseInt(name.substring("profile_".length()));
        }
        catch (NumberFormatException notASlot) {
            return -1;
        }
    }
}
