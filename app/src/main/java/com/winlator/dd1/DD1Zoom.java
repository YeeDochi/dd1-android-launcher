package com.winlator.dd1;

// The magnifier: the game's picture drawn half as large again, around a centre
// the player chooses. It is the game's own view that is scaled, so the runtime
// draws exactly as before and only the screen-to-picture mapping changes - a
// fingertip on the screen is first taken back to where it lies in the unscaled
// view, and from there the overlay maps it to the desktop as it always has.
//
// The runtime has a magnifier of its own, but it centres on the pointer, and with
// the finger as the pointer every tap would move the picture under the next one.
//
// Everything here is in the view's own pixels. The picture is the part of the
// view the game is drawn in; the bars either side stay where the buttons are.
public final class DD1Zoom {
    public static final float FACTOR = 1.5f;

    private float left, top, width, height;
    private float centreX, centreY;
    private boolean on;

    public void setPicture(float left, float top, float width, float height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        if (on) panTo(centreX, centreY);
    }

    public boolean on() {
        return on;
    }

    // Turning it on looks at the place the player last touched, which is what
    // they were trying to read.
    public boolean toggle(float focusX, float focusY) {
        on = !on;
        if (on) panTo(focusX, focusY);
        return on;
    }

    public void off() {
        on = false;
    }

    // The centre is kept far enough in that the enlarged picture always fills
    // the place the picture had: there is no black edge to pan into.
    public void panTo(float x, float y) {
        float halfW = width / (2f * FACTOR);
        float halfH = height / (2f * FACTOR);
        centreX = clamp(x, left + halfW, left + width - halfW);
        centreY = clamp(y, top + halfH, top + height - halfH);
    }

    // Pans to a place given as a fraction of the picture, which is what the map
    // under the game shows.
    public void panToFraction(float fx, float fy) {
        panTo(left + fx * width, top + fy * height);
    }

    public float scale() {
        return on ? FACTOR : 1f;
    }

    // With the pivot at the view's top left: screen = local * scale + translation.
    public float translationX() {
        return on ? left + width * 0.5f - centreX * FACTOR : 0f;
    }

    public float translationY() {
        return on ? top + height * 0.5f - centreY * FACTOR : 0f;
    }

    public float[] toLocal(float screenX, float screenY) {
        float scale = scale();
        return new float[]{(screenX - translationX()) / scale, (screenY - translationY()) / scale};
    }

    // The part of the picture on screen, as fractions of it: left, top, right, bottom.
    public float[] visibleFraction() {
        if (!on || width <= 0 || height <= 0) return new float[]{0f, 0f, 1f, 1f};
        float halfW = width / (2f * FACTOR);
        float halfH = height / (2f * FACTOR);
        return new float[]{
            (centreX - halfW - left) / width, (centreY - halfH - top) / height,
            (centreX + halfW - left) / width, (centreY + halfH - top) / height};
    }

    private static float clamp(float value, float min, float max) {
        return min > max ? (min + max) * 0.5f : Math.max(min, Math.min(max, value));
    }
}
