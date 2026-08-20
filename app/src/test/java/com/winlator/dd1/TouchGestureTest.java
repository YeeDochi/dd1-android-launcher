package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TouchGestureTest {
    private final List<String> events = new ArrayList<>();
    private final TouchGesture gesture = new TouchGesture(new TouchGesture.Listener() {
        @Override public void onMove(float x, float y) { events.add("move " + (int)x + "," + (int)y); }
        @Override public void onPress() { events.add("press"); }
        @Override public void onRelease() { events.add("release"); }
        @Override public void onHoverStart() { events.add("hover"); }
    });

    // The pointer has to arrive before the button does, or the click lands
    // wherever the cursor happened to be.
    @Test
    public void aQuickTapClicks() {
        gesture.down(100, 100, 0);
        gesture.up(100, 100, 80);

        assertEquals(Arrays.asList("move 100,100", "press", "release"), events);
    }

    // DD1's own drag is a mouse drag, so the button has to go down before the
    // pointer travels, not after it arrives.
    @Test
    public void movingBeforeTheHoldBecomesADrag() {
        gesture.down(100, 100, 0);
        gesture.move(160, 100, 40);
        gesture.up(160, 100, 90);

        assertEquals(Arrays.asList(
            "move 100,100", "press", "move 160,100", "release"), events);
    }

    // Skill and trinket descriptions only exist under a resting cursor, so a
    // hold has to park the pointer without ever clicking - releasing must not
    // click either, or every tooltip would also activate what it described.
    @Test
    public void holdingStillStartsHoverWithoutPressing() {
        gesture.down(100, 100, 0);
        gesture.tick(600);
        gesture.up(100, 100, 700);

        assertEquals(Arrays.asList("move 100,100", "hover"), events);
    }

    // Reading one tooltip and then the next should not need a new hold, so the
    // pointer keeps following the finger once hovering has begun.
    @Test
    public void hoverKeepsTrackingTheFingerWithoutClicking() {
        gesture.down(100, 100, 0);
        gesture.tick(600);
        gesture.move(140, 120, 700);
        gesture.up(140, 120, 800);

        assertEquals(Arrays.asList("move 100,100", "hover", "move 140,120"), events);
    }

    // A finger resting on glass is never quite still, and that wobble must not
    // be mistaken for the beginning of a drag.
    @Test
    public void aSlowTapWithoutRealMovementStillHovers() {
        gesture.down(10, 10, 0);
        gesture.move(12, 11, 100);
        gesture.tick(600);

        assertEquals(Arrays.asList("move 10,10", "hover"), events);
    }

    // The tooltip appears at the cursor, so a cursor under the fingertip puts the
    // text under the hand holding the phone.
    @Test
    public void theCursorSitsAboveTheFingerWhileHovering() {
        assertEquals(0f, TouchGesture.hoverOffset(false), 0.01);
        assertEquals(-TouchGesture.HOVER_LIFT, TouchGesture.hoverOffset(true), 0.01);
    }
}
