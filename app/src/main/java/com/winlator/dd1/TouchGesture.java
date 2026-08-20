package com.winlator.dd1;

// Turns one finger's down, move and up, plus a clock, into the four things the
// X server understands: where the pointer is, and whether the button is held.
public final class TouchGesture {
    public interface Listener {
        void onMove(float x, float y);

        void onPress();

        void onRelease();

        void onHoverStart();
    }

    // Far enough that the finger meant to travel rather than to sit still.
    private static final float SLOP = 16f;

    // Long enough that an ordinary tap is never read as a request for a tooltip.
    private static final long HOLD_MILLIS = 350L;

    // How far above the fingertip the cursor sits while hovering, in pixels.
    public static final float HOVER_LIFT = 48f;

    public static float hoverOffset(boolean hovering) {
        return hovering ? -HOVER_LIFT : 0f;
    }

    private final Listener listener;
    private float downX;
    private float downY;
    private long downAt;
    private boolean pressed;
    private boolean hovering;

    public TouchGesture(Listener listener) {
        this.listener = listener;
    }

    public void down(float x, float y, long timeMillis) {
        downX = x;
        downY = y;
        downAt = timeMillis;
        pressed = false;
        hovering = false;
        listener.onMove(x, y);
    }

    public void move(float x, float y, long timeMillis) {
        if (!pressed && !hovering) {
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
        if (hovering) return;
        if (!pressed) listener.onPress();
        listener.onRelease();
    }

    // The hold cannot be noticed by a finger that is doing nothing, so the caller
    // drives a clock.
    public void tick(long timeMillis) {
        if (pressed || hovering || timeMillis - downAt < HOLD_MILLIS) return;
        hovering = true;
        listener.onHoverStart();
    }
}
