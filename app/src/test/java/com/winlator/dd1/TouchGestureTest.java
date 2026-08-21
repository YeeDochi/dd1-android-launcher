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
        @Override public void onSecondaryClick() { events.add("right"); }
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

    // Using an item on a hero is a right click and nothing else in this game is,
    // so holding still is what asks for one. The hold was parking the cursor for
    // tooltips before, which turned out to be work nobody needed: the cursor
    // stays where a tap left it, so a tap already opens the tooltip.
    @Test
    public void holdingStillAsksForARightClick() {
        gesture.down(100, 100, 0);
        gesture.tick(600);
        gesture.up(100, 100, 700);

        assertEquals(Arrays.asList("move 100,100", "right"), events);
    }

    // One hold is one right click. Letting the finger wander afterwards must not
    // fire another, and lifting must not add a left click on top.
    @Test
    public void aHoldClicksOnceAndTheFingerIsFreeAfterwards() {
        gesture.down(100, 100, 0);
        gesture.tick(600);
        gesture.tick(900);
        gesture.move(140, 120, 950);
        gesture.up(140, 120, 1000);

        assertEquals(Arrays.asList("move 100,100", "right", "move 140,120"), events);
    }

    // A finger resting on glass is never quite still, and that wobble must not
    // be mistaken for the beginning of a drag.
    @Test
    public void aSlowTapWithoutRealMovementStillRightClicks() {
        gesture.down(10, 10, 0);
        gesture.move(12, 11, 100);
        gesture.tick(600);

        assertEquals(Arrays.asList("move 10,10", "right"), events);
    }
}
