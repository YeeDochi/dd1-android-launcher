package io.github.dd1android.launcher.xserver.events;

import io.github.dd1android.launcher.xserver.util.Bitmask;
import io.github.dd1android.launcher.xserver.Window;

public class MotionNotify extends InputDeviceEvent {
    public MotionNotify(boolean detail, Window root, Window event, Window child, short rootX, short rootY, short eventX, short eventY, Bitmask state) {
        super(6, (byte)(detail ? 1 : 0), root, event, child, rootX, rootY, eventX, eventY, state);
    }
}
