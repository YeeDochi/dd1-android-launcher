package com.winlator.dd1;

// Turns one finger's down, move and up, plus a clock, into the four things the
// X server understands: where the pointer is, and whether the button is held.
public final class TouchGesture {
    public interface Listener {
        void onMove(float x, float y);

        void onPress();

        void onRelease();

        void onSecondaryClick();
    }

    // Far enough that the finger meant to travel rather than to sit still.
    private static final float SLOP = 16f;

    // Long enough that an ordinary tap is never read as a request for a right
    // click. The caller needs it to know when to run the clock.
    public static final long HOLD_MILLIS = 350L;

    private final Listener listener;
    private float downX;
    private float downY;
    private long downAt;
    private boolean pressed;
    private boolean held;

    public TouchGesture(Listener listener) {
        this.listener = listener;
    }

    public void down(float x, float y, long timeMillis) {
        downX = x;
        downY = y;
        downAt = timeMillis;
        pressed = false;
        held = false;
        listener.onMove(x, y);
    }

    public void move(float x, float y, long timeMillis) {
        if (!pressed && !held) {
            if (Math.hypot(x - downX, y - downY) < SLOP) return;
            listener.onPress();
            pressed = true;
        }
        listener.onMove(x, y);
    }

    // Nothing is pressed until the finger travels or leaves, because a press that
    // landed on the way down could not be taken back once the touch turns out to
    // be a hold.
    public void up(float x, float y, long timeMillis) {
        if (held) return;
        if (!pressed) listener.onPress();
        listener.onRelease();
    }

    // The hold cannot be noticed by a finger that is doing nothing, so the caller
    // drives a clock. One hold is one click: the finger is free afterwards and
    // lifting adds nothing.
    public void tick(long timeMillis) {
        if (pressed || held || timeMillis - downAt < HOLD_MILLIS) return;
        held = true;
        listener.onSecondaryClick();
    }
}
