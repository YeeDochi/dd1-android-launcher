package com.winlator.dd1;

// Whether the overlay is holding Shift and Ctrl down. The game toggles its torch
// with Shift+Ctrl+left click, and a touch screen has no way to hold two keys and
// tap at the same time, so a button holds them until it is pressed again.
//
// The keys themselves are held inside the X server. This only remembers that they
// are, which matters at the one moment it is easy to forget: leaving the game
// while they are down leaves them down for whatever runs next.
public final class DD1HeldKeys {
    private boolean held;

    public boolean held() {
        return held;
    }

    public boolean toggle() {
        held = !held;
        return held;
    }

    // True when something was being held and now has to be let go of.
    public boolean releaseAll() {
        boolean was = held;
        held = false;
        return was;
    }
}
