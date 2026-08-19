package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Which owned DLC the launcher installs. Ownership comes from Steam, the choice
// comes from the user, and anything bought later is included until they say
// otherwise.
public final class DlcSelection {
    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put(580100, "The Crimson Court");
        NAMES.put(702540, "The Shieldbreaker");
        NAMES.put(735730, "The Color of Madness");
        NAMES.put(1117860, "The Butcher's Circus");
        NAMES.put(4964110, "The Fire's Edge");
    }

    private final List<Integer> owned = new ArrayList<>();
    private final Set<Integer> excluded = new LinkedHashSet<>();

    private DlcSelection() {}

    public static String nameOf(int appId) {
        String name = NAMES.get(appId);
        return name != null ? name : "DLC " + appId;
    }

    // The stored value lists what the user turned off, so content bought after
    // the choice was made still arrives selected.
    public static DlcSelection parse(String stored, Collection<Integer> ownedAppIds) {
        DlcSelection selection = new DlcSelection();
        for (int appId : ownedAppIds) {
            if (appId != DD1SteamEvents.APP_ID && !selection.owned.contains(appId))
                selection.owned.add(appId);
        }
        if (stored != null) {
            for (String piece : stored.split(",")) {
                try {
                    selection.excluded.add(Integer.parseInt(piece.trim()));
                }
                catch (NumberFormatException ignored) {}
            }
        }
        return selection;
    }

    public List<Integer> owned() {
        return new ArrayList<>(owned);
    }

    public boolean isSelected(int appId) {
        return owned.contains(appId) && !excluded.contains(appId);
    }

    public void setSelected(int appId, boolean selected) {
        if (selected) excluded.remove(appId);
        else excluded.add(appId);
    }

    public List<Integer> selected() {
        List<Integer> result = new ArrayList<>();
        for (int appId : owned) {
            if (!excluded.contains(appId)) result.add(appId);
        }
        return result;
    }

    public String serialize() {
        StringBuilder text = new StringBuilder();
        for (int appId : excluded) {
            if (text.length() > 0) text.append(',');
            text.append(appId);
        }
        return text.toString();
    }
}
