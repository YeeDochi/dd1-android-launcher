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
    private final List<Row> windowsRows;

    private DD1DepotCatalog(Map<Integer, Row> byAppId, List<Row> windowsRows) {
        this.byAppId = byAppId;
        this.windowsRows = windowsRows;
    }

    public static DD1DepotCatalog of(List<Row> rows) {
        Map<Integer, Row> windows = new LinkedHashMap<>();
        List<Row> all = new java.util.ArrayList<>();
        for (Row row : rows) {
            if (!"windows".equals(row.os)) continue;
            all.add(row);
            if (row.dlcAppId > 0) windows.put(row.dlcAppId, row);
        }
        return new DD1DepotCatalog(windows, all);
    }

    public static DD1DepotCatalog empty() {
        return new DD1DepotCatalog(Collections.emptyMap(), Collections.<Row>emptyList());
    }

    // Steam hands out every depot it has unless it is told which ones to take,
    // and an unfiltered download fetched the unwanted DLC and deleted it at the
    // end. The base game carries no dlcappid and is always part of the answer.
    //
    // An empty catalog answers with an empty list on purpose: the downloader
    // reads that as "no preference" and fetches everything, which is the old
    // behaviour and the only safe thing to do when the depots are not known yet.
    public List<Integer> depotsFor(java.util.Collection<Integer> selectedDlc) {
        List<Integer> depots = new java.util.ArrayList<>();
        for (Row row : windowsRows) {
            if (row.dlcAppId <= 0 || selectedDlc.contains(row.dlcAppId))
                depots.add(row.depotId);
        }
        return depots;
    }

    // Which appids Steam itself calls DLC of this game: the ones its depot table
    // names. This is the list, not a list somebody typed out.
    public java.util.Set<Integer> dlcAppIds() {
        return Collections.unmodifiableSet(byAppId.keySet());
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
