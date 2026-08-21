package com.winlator.dd1;

// The IME can disappear through Android's Back gesture instead of the ABC
// button, so portrait mode cannot be inferred from the IME's current inset.
final class DD1KeyboardMode {
    private boolean active;

    boolean toggle() {
        active = !active;
        return active;
    }

    boolean active() {
        return active;
    }

    boolean onImeHidden() {
        if (!active) return false;
        active = false;
        return true;
    }
}
