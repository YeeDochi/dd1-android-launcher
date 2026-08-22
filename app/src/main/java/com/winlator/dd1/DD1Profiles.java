package com.winlator.dd1;

import android.content.Context;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;

// Reading the runtime's profiles can throw, and the launcher reads them often.
// ContainerManager hands the bytes of a config it could not open straight to
// new String(...), so a profile directory without one is a NullPointerException
// out of the constructor - and a profile being made right now has no config yet:
// it is created, a tar is unpacked into it, and only then is the config written.
//
// A download publishes a snapshot several times a second and each one reaches
// refresh(), so a first run that downloads the game reads that half-made
// directory over and over. It crashed on whichever read landed in the gap.
//
// DD1ProfileRepair clears up the ones left behind by a process that died. This is
// the other half: a read that happens to land mid-creation gives back "no profile
// yet" instead of taking the app down.
public abstract class DD1Profiles {
    private DD1Profiles() {}

    // Null when the profiles cannot be read right now.
    public static ContainerManager manager(Context context) {
        try {
            return new ContainerManager(context);
        }
        catch (Throwable beingWritten) {
            return null;
        }
    }

    public static Container first(ContainerManager manager) {
        if (manager == null) return null;
        try {
            return manager.getContainers().isEmpty() ? null : manager.getContainers().get(0);
        }
        catch (Throwable beingWritten) {
            return null;
        }
    }

    public static Container first(Context context) {
        return first(manager(context));
    }
}
