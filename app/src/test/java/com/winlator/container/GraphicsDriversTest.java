package com.winlator.container;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GraphicsDriversTest {
    @Test
    public void disablesNativeVortekOnTranslatedX86Hosts() {
        assertFalse(GraphicsDrivers.isVortekRendererSupported("x86_64"));
        assertFalse(GraphicsDrivers.isVortekRendererSupported("x86"));
        assertTrue(GraphicsDrivers.isVortekRendererSupported("arm64-v8a"));
    }
}
