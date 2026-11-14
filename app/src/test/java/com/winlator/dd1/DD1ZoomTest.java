package com.winlator.dd1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DD1ZoomTest {
    private static final float EPS = 0.01f;

    // A 2340x1080 phone with a 1920x1080 picture between two 210 px bars.
    private DD1Zoom phone() {
        DD1Zoom zoom = new DD1Zoom();
        zoom.setPicture(210, 0, 1920, 1080);
        return zoom;
    }

    @Test
    public void offMapsTheScreenAsItIs() {
        DD1Zoom zoom = phone();
        assertFalse(zoom.on());
        assertArrayEquals(new float[]{500, 300}, zoom.toLocal(500, 300), EPS);
        assertArrayEquals(new float[]{0, 0, 1, 1}, zoom.visibleFraction(), EPS);
    }

    @Test
    public void theCentreOfThePictureStaysUnderTheCentreOfTheScreen() {
        DD1Zoom zoom = phone();
        assertTrue(zoom.toggle(1170, 540));
        assertEquals(1.5f, zoom.scale(), EPS);
        assertArrayEquals(new float[]{1170, 540}, zoom.toLocal(1170, 540), EPS);
        // A third of the picture, centred.
        assertArrayEquals(new float[]{1f / 6, 1f / 6, 5f / 6, 5f / 6}, zoom.visibleFraction(), EPS);
    }

    @Test
    public void aFingerLandsOnWhatIsDrawnUnderIt() {
        DD1Zoom zoom = phone();
        zoom.toggle(800, 400);
        float[] local = zoom.toLocal(1000, 700);
        float screenX = local[0] * zoom.scale() + zoom.translationX();
        float screenY = local[1] * zoom.scale() + zoom.translationY();
        assertArrayEquals(new float[]{1000, 700}, new float[]{screenX, screenY}, EPS);
    }

    @Test
    public void panningStopsAtTheEdgeOfThePicture() {
        DD1Zoom zoom = phone();
        zoom.toggle(0, 0);
        float[] seen = zoom.visibleFraction();
        assertEquals(0f, seen[0], EPS);
        assertEquals(0f, seen[1], EPS);
        // The picture's top left corner is drawn where it was before.
        assertArrayEquals(new float[]{210, 0}, zoom.toLocal(210, 0), EPS);

        zoom.panToFraction(1, 1);
        seen = zoom.visibleFraction();
        assertEquals(1f, seen[2], EPS);
        assertEquals(1f, seen[3], EPS);
        assertArrayEquals(new float[]{2130, 1080}, zoom.toLocal(2130, 1080), EPS);
    }

    @Test
    public void aNewPictureSizeKeepsTheCentreInside() {
        DD1Zoom zoom = phone();
        zoom.toggle(2000, 1000);
        zoom.setPicture(0, 0, 1000, 500);
        float[] seen = zoom.visibleFraction();
        assertEquals(1f, seen[2], EPS);
        assertEquals(1f, seen[3], EPS);
    }

    @Test
    public void turningItOffForgetsTheScale() {
        DD1Zoom zoom = phone();
        zoom.toggle(500, 500);
        zoom.off();
        assertEquals(1f, zoom.scale(), EPS);
        assertEquals(0f, zoom.translationX(), EPS);
    }
}
