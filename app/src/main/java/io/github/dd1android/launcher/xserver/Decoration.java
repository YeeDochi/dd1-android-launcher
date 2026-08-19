package io.github.dd1android.launcher.xserver;

public enum Decoration {
    ALL, BORDER, RESIZEH, TITLE, MENU, MINIMIZE, MAXIMIZE;

    public int flag() {
        return 1 << ordinal();
    }
}
