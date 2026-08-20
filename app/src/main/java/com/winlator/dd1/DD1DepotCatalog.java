package com.winlator.dd1;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Which depot holds a DLC, and which version of it Steam is offering. Read from
// the game's own PICS entry: every DLC ships three depots, one per platform, and
// their order is not fixed - The Fire's Edge is windows, linux, macos where the
// others are windows, macos, linux - so the platform is read, never counted.
public final class DD1DepotCatalog {
    public static final class Row {
        public final int depotId;
        public final int dlcAppId;
        public final String os;
        public final String manifest;

        public Row(int depotId, int dlcAppId, String os, String manifest) {
            this.depotId = depotId;
            this.dlcAppId = dlcAppId;
            this.os = os;
            this.manifest = manifest;
        }
    }

    private final Map<Integer, Row> byAppId;

    private DD1DepotCatalog(Map<Integer, Row> byAppId) {
        this.byAppId = byAppId;
    }

    public static DD1DepotCatalog of(List<Row> rows) {
        Map<Integer, Row> windows = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.dlcAppId <= 0 || !"windows".equals(row.os)) continue;
            windows.put(row.dlcAppId, row);
        }
        return new DD1DepotCatalog(windows);
    }

    public static DD1DepotCatalog empty() {
        return new DD1DepotCatalog(Collections.emptyMap());
    }

    // 0 when this account's DLC has no windows depot, which is not something to
    // guess a number for.
    public int depotOf(int dlcAppId) {
        Row row = byAppId.get(dlcAppId);
        return row == null ? 0 : row.depotId;
    }

    public String manifestOf(int dlcAppId) {
        Row row = byAppId.get(dlcAppId);
        return row == null ? null : row.manifest;
    }
}
