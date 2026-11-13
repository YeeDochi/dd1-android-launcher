package com.winlator.dd1;

// A container's drives are one string of letter-colon-path entries run together,
// "D:/storage/emulated/0/DownloadE:/data/...". The game is G:, and when it moves
// the old G: has to go: appending a second G: would leave Wine choosing between
// two places, one of which the game has left.
//
// Read the way Container.drivesIterator reads it - a letter is the character
// before each colon, and a path runs to the character before the next colon -
// but without loading Container, whose static fields need a running Android.
public final class DD1Drives {
    private DD1Drives() {}

    public static String withGame(String drives, String gamePath) {
        StringBuilder kept = new StringBuilder();
        String all = drives == null ? "" : drives;
        int colon = all.indexOf(':');
        while (colon > 0) {
            int next = all.indexOf(':', colon + 1);
            char letter = all.charAt(colon - 1);
            String path = all.substring(colon + 1, next != -1 ? next - 1 : all.length());
            if (Character.toUpperCase(letter) != 'G') kept.append(letter).append(':').append(path);
            colon = next;
        }
        return kept.append("G:").append(gamePath).toString();
    }
}
