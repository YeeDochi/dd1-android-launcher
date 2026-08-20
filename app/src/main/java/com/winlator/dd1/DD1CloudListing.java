package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// What Steam says it holds, and whether it managed to say anything at all. The
// difference matters more than the contents: another launcher read a failed
// listing as an empty cloud and let fresh defaults overwrite real progress.
public final class DD1CloudListing {
    private final boolean known;
    private final long changeNumber;
    private final List<DD1SaveSummary.Entry> files;

    private DD1CloudListing(boolean known, long changeNumber,
            List<DD1SaveSummary.Entry> files) {
        this.known = known;
        this.changeNumber = changeNumber;
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
    }

    public static DD1CloudListing of(long changeNumber, List<DD1SaveSummary.Entry> files) {
        return new DD1CloudListing(true, changeNumber, files);
    }

    public static DD1CloudListing unknown() {
        return new DD1CloudListing(false, 0L,
            Collections.<DD1SaveSummary.Entry>emptyList());
    }

    public boolean known() {
        return known;
    }

    public long changeNumber() {
        return changeNumber;
    }

    public List<DD1SaveSummary.Entry> files() {
        return files;
    }

    // Steam hands the digest over as bytes and the time as a Date; the local side
    // holds hex and milliseconds, and the two have to compare.
    public static DD1SaveSummary.Entry entry(String name, int size, byte[] sha1,
            long millis) {
        StringBuilder hex = new StringBuilder();
        if (sha1 != null) for (byte value : sha1) hex.append(String.format("%02x", value));
        return new DD1SaveSummary.Entry(name, size, millis, hex.toString());
    }
}
