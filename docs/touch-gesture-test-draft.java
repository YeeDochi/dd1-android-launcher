package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TouchGestureTest {
    private final List<String> events = new ArrayList<>();
    private final TouchGesture gesture = new TouchGesture(new TouchGesture.Listener() {
        @Override public void onMove(float x, float y) { events.add("move " + (int)x + "," + (int)y); }
        @Override public void onPress() { events.add("press"); }
        @Override public void onRelease() { events.add("release"); }
        @Override public void onHoverStart() { events.add("hover"); }
    });

    @Test
    public void aQuickTapClicks() {
        gesture.down(100, 100, 0);
        gesture.up(100, 100, 80);

        assertEquals(java.util.Arrays.asList("move 100,100", "press", "release"), events);
    }

    @Test
    public void movingBeforeTheHoldBecomesADrag() {
        gesture.down(100, 100, 0);
        gesture.move(160, 100, 40);
        gesture.up(160, 100, 90);

        assertEquals(java.util.Arrays.asList(
            "move 100,100", "press", "move 160,100", "release"), events);
    }

    @Test
    public void holdingStillStartsHoverWithoutPressing() {
        gesture.down(100, 100, 0);
        gesture.tick(600);
        gesture.up(100, 100, 700);

        assertEquals(java.util.Arrays.asList("move 100,100", "hover"), events);
    }

    @Test
    public void hoverKeepsTrackingTheFingerWithoutClicking() {
        gesture.down(100, 100, 0);
        gesture.tick(600);
        gesture.move(140, 120, 700);
        gesture.up(140, 120, 800);

        assertEquals(java.util.Arrays.asList("move 100,100", "hover", "move 140,120"), events);
    }

    @Test
    public void aSlowTapWithoutMovementStillHovers() {
        gesture.down(10, 10, 0);
        gesture.move(12, 11, 100);
        gesture.tick(600);

        assertEquals(java.util.Arrays.asList("move 10,10", "hover"), events);
    }

    @Test
    public void theCursorSitsAboveTheFingerWhileHovering() {
        assertEquals(0, TouchGesture.hoverOffset(false));
        assertEquals(-TouchGesture.HOVER_LIFT, TouchGesture.hoverOffset(true));
    }
}
