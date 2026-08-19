package io.github.dd1android.launcher.xserver.events;

import io.github.dd1android.launcher.xserver.util.Bitmask;
import io.github.dd1android.launcher.xserver.Window;

public class KeyRelease extends InputDeviceEvent {
    public KeyRelease(byte keycode, Window root, Window event, Window child, short rootX, short rootY, short eventX, short eventY, Bitmask state) {
        super(3, keycode, root, event, child, rootX, rootY, eventX, eventY, state);
    }
}
