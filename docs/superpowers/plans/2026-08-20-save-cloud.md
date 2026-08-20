# Steam Cloud save transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move saves between the phone and Steam Cloud, so a campaign started on the phone can be finished on the PC and the other way round.

**Architecture:** Steam hands out an HTTP request per block and the launcher performs it, so the work splits into a transfer helper that speaks HTTP, a listing that says what the cloud holds, download, upload, and the decision that chooses between them. The decision never resolves a conflict on its own.

**Tech Stack:** Java 8, javasteam 1.8.0's `SteamCloud` handler, okhttp (already on the classpath through javasteam), JUnit 4. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-19-dd1-android-launcher-design.md`, "PC save sharing". Its local half is done: `docs/superpowers/plans/2026-08-20-save-snapshots.md`.

## Global Constraints

Measured against the real account on 2026-08-20, not assumed:

- `getAppFileListChange(262060, 0)` answered `change=1`, `delta=false`,
  `prefixes=[]`, `machines=[<unknown>]`, and one file: `steam_init.json`, 166
  bytes, persist state `Persisted`. **The cloud holds no profile saves yet**, so
  there is nothing to download until something is uploaded, and nothing to lose
  while testing upload.
- Remote names are plain relative paths. `getPathPrefixes()` was empty; when it
  is not, a file's name is its prefix plus its filename, chosen by
  `getPathPrefixIndex()`.
- `AppFileInfo` carries `getShaFile()`, so remote and local compare on SHA-1
  directly. `DD1SaveSummary` already produces the local side.
- Upload is `beginAppUploadBatch` → per file `beginFileUpload` → the HTTP blocks
  it returns, performed by us → `commitFileUpload(sha, filename)` →
  `completeAppUploadBatch`. `FileUploadInfo.getEncryptFile()` must be checked:
  this plan refuses to upload when it is true rather than guessing at the
  cipher.
- Download is `clientFileDownload` → one HTTP GET we perform, then SHA-1 must
  match `getShaFile()` before the bytes go anywhere near the save tree.
- `getAppFileListChange` takes a `kotlinx.coroutines.CoroutineScope`; Java cannot
  reach Kotlin's default-argument bridge, so pass
  `kotlinx.coroutines.GlobalScope.INSTANCE`. This compiles and ran.

Rules inherited from `iunius612/StS2-Launcher_Mod_Manager` (MIT, credited in
`NOTICE`), which lost saves before it learned them:

- **An unknown cloud state is not an empty one.** A listing that failed means
  local-only: no upload, no delete, no "the cloud has nothing".
- **One funnel for cloud writes.** Every upload goes through a single method that
  refuses an empty set and a zero-length file. Scattering that check is how one
  path ends up without it.
- **Timestamps decide nothing.** SHA-1 decides.

And from this project's own history:

- Snapshot before any transfer touches the save tree. `DD1SaveSnapshots.take`
  exists; the cloud path calls it, and a failed snapshot cancels the transfer.
- Verify by content. A downloaded file whose digest does not match is discarded,
  not written.
- Every task ends green on `./gradlew assembleDebug testDebugUnitTest`.

---

### Task 1: The transfer helper

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1CloudTransfer.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1CloudTransferTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `static String url(String host, String path, boolean useHttps)`;
  `static boolean digestMatches(byte[] content, byte[] expectedSha1)`;
  `static byte[] inflate(byte[] body, int rawSize)` returning the body unchanged
  when it is already `rawSize` long and zlib-inflating it otherwise.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

public class DD1CloudTransferTest {
    @Test
    public void aBlockRequestBecomesAUrl() {
        assertEquals("https://host.example/path?token=1",
            DD1CloudTransfer.url("host.example", "/path?token=1", true));
        assertEquals("http://host.example/path",
            DD1CloudTransfer.url("host.example", "/path", false));
    }

    // Steam gives the digest it expects, and a file whose bytes do not match it
    // has no business anywhere near a save tree.
    @Test
    public void contentIsCheckedAgainstTheDigestSteamGave() {
        byte[] content = "abc".getBytes();
        byte[] sha1 = new byte[] {
            (byte)0xa9, (byte)0x99, (byte)0x3e, (byte)0x36, (byte)0x47,
            (byte)0x06, (byte)0x81, (byte)0x6a, (byte)0xba, (byte)0x3e,
            (byte)0x25, (byte)0x71, (byte)0x78, (byte)0x50, (byte)0xc2,
            (byte)0x6c, (byte)0x9c, (byte)0xd0, (byte)0xd8, (byte)0x9d};

        assertTrue(DD1CloudTransfer.digestMatches(content, sha1));
        assertFalse(DD1CloudTransfer.digestMatches("abd".getBytes(), sha1));
    }

    @Test
    public void anUncompressedBodyIsLeftAlone() {
        byte[] content = "hello".getBytes();

        assertArrayEquals(content, DD1CloudTransfer.inflate(content, content.length));
    }

    @Test
    public void aCompressedBodyIsInflatedToItsRawSize() {
        byte[] content = "hello hello hello hello".getBytes();
        byte[] squashed = deflate(content);

        assertArrayEquals(content, DD1CloudTransfer.inflate(squashed, content.length));
    }

    private static byte[] deflate(byte[] content) {
        Deflater deflater = new Deflater();
        deflater.setInput(content);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return out.toByteArray();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1CloudTransferTest*'`
Expected: FAIL to compile, "cannot find symbol" for `DD1CloudTransfer`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

// Steam does not move save bytes itself: it hands out the request to make and
// the digest to expect. This is the part that speaks HTTP's language without
// knowing anything about saves.
public final class DD1CloudTransfer {
    private DD1CloudTransfer() {}

    public static String url(String host, String path, boolean useHttps) {
        return (useHttps ? "https://" : "http://") + host + path;
    }

    public static boolean digestMatches(byte[] content, byte[] expectedSha1) {
        if (expectedSha1 == null || expectedSha1.length == 0) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return Arrays.equals(digest.digest(content), expectedSha1);
        }
        catch (NoSuchAlgorithmException impossible) {
            return false;
        }
    }

    // Steam reports both sizes; a body already the raw length was never squashed.
    public static byte[] inflate(byte[] body, int rawSize) {
        if (body.length == rawSize) return body;
        Inflater inflater = new Inflater();
        inflater.setInput(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream(rawSize);
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0) break;
                out.write(buffer, 0, read);
            }
        }
        catch (DataFormatException notCompressed) {
            return body;
        }
        finally {
            inflater.end();
        }
        return out.toByteArray();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1CloudTransfer.java app/src/test/java/com/winlator/dd1/DD1CloudTransferTest.java
git commit -m "feat: speak the HTTP half of a Steam Cloud transfer"
```

---

### Task 2: What the cloud holds

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1CloudListing.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1CloudListingTest.java`

**Interfaces:**
- Consumes: `DD1SaveSummary.Entry` from the local plan.
- Produces: `DD1CloudListing.of(long changeNumber, List<Entry> files)`;
  `static DD1CloudListing unknown()`; `boolean known()`; `long changeNumber()`;
  `List<Entry> files()`; `static Entry entry(String name, int size, byte[] sha1, long millis)`
  turning Steam's fields into the same shape the local side uses.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DD1CloudListingTest {
    // A listing that could not be read is the dangerous case: read as empty, it
    // invites an upload that deletes a PC's progress.
    @Test
    public void anUnknownListingIsNotAnEmptyOne() {
        DD1CloudListing unknown = DD1CloudListing.unknown();

        assertFalse(unknown.known());
        assertEquals(0, unknown.files().size());
    }

    @Test
    public void anEmptyCloudIsKnownToBeEmpty() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Collections.emptyList());

        assertTrue(listing.known());
        assertEquals(1L, listing.changeNumber());
        assertEquals(0, listing.files().size());
    }

    @Test
    public void steamsFieldsBecomeTheSameShapeAsALocalSummary() {
        byte[] sha1 = new byte[] {1, 2, 3};

        DD1SaveSummary.Entry entry =
            DD1CloudListing.entry("profile_0/persist.game.json", 2140, sha1, 5000L);

        assertEquals("profile_0/persist.game.json", entry.path);
        assertEquals(2140, entry.length);
        assertEquals(5000L, entry.modifiedMillis);
        assertEquals("010203", entry.sha1);
    }

    @Test
    public void theListingKeepsWhatItWasGiven() {
        DD1CloudListing listing = DD1CloudListing.of(7L, Arrays.asList(
            DD1CloudListing.entry("steam_init.json", 166, new byte[] {(byte)0xff}, 1L)));

        assertEquals(1, listing.files().size());
        assertEquals("ff", listing.files().get(0).sha1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1CloudListingTest*'`
Expected: FAIL to compile, "cannot find symbol" for `DD1CloudListing`.

- [ ] **Step 3: Write minimal implementation**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1CloudListing.java app/src/test/java/com/winlator/dd1/DD1CloudListingTest.java
git commit -m "feat: tell an empty cloud from one we could not read"
```

---

### Task 3: The decision

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1CloudPlan.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1CloudPlanTest.java`

**Interfaces:**
- Consumes: `DD1CloudListing`, `DD1SaveSummary.Entry`.
- Produces: `enum DD1CloudPlan.Action { NOTHING, UPLOAD, DOWNLOAD, CONFLICT, LOCAL_ONLY }`;
  `static DD1CloudPlan between(List<Entry> local, DD1CloudListing cloud, List<Entry> lastSynced)`;
  `Action action()`; `List<String> paths()` naming the files the action would move.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DD1CloudPlanTest {
    private static final List<DD1SaveSummary.Entry> NONE = Collections.emptyList();

    @Test
    public void anUnreadableCloudMeansLocalOnly() {
        DD1CloudPlan plan = DD1CloudPlan.between(
            Arrays.asList(entry("profile_0/persist.game.json", "aaa")),
            DD1CloudListing.unknown(), NONE);

        assertEquals(DD1CloudPlan.Action.LOCAL_ONLY, plan.action());
        assertEquals(0, plan.paths().size());
    }

    @Test
    public void matchingSidesNeedNothing() {
        List<DD1SaveSummary.Entry> same =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));

        assertEquals(DD1CloudPlan.Action.NOTHING,
            DD1CloudPlan.between(same, DD1CloudListing.of(1L, same), same).action());
    }

    // Only the phone moved since the last sync, so the phone is right.
    @Test
    public void localAloneMovingIsAnUpload() {
        List<DD1SaveSummary.Entry> synced =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));
        List<DD1SaveSummary.Entry> local =
            Arrays.asList(entry("profile_0/persist.game.json", "bbb"));

        DD1CloudPlan plan = DD1CloudPlan.between(local, DD1CloudListing.of(1L, synced), synced);

        assertEquals(DD1CloudPlan.Action.UPLOAD, plan.action());
        assertEquals(Arrays.asList("profile_0/persist.game.json"), plan.paths());
    }

    @Test
    public void theCloudAloneMovingIsADownload() {
        List<DD1SaveSummary.Entry> synced =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));
        List<DD1SaveSummary.Entry> cloud =
            Arrays.asList(entry("profile_0/persist.game.json", "ccc"));

        DD1CloudPlan plan = DD1CloudPlan.between(synced, DD1CloudListing.of(2L, cloud), synced);

        assertEquals(DD1CloudPlan.Action.DOWNLOAD, plan.action());
        assertEquals(Arrays.asList("profile_0/persist.game.json"), plan.paths());
    }

    // Both moved. Nothing here picks a winner; the player does.
    @Test
    public void bothMovingIsAConflictAndStaysOne() {
        List<DD1SaveSummary.Entry> synced =
            Arrays.asList(entry("profile_0/persist.game.json", "aaa"));
        List<DD1SaveSummary.Entry> local =
            Arrays.asList(entry("profile_0/persist.game.json", "bbb"));
        List<DD1SaveSummary.Entry> cloud =
            Arrays.asList(entry("profile_0/persist.game.json", "ccc"));

        DD1CloudPlan plan = DD1CloudPlan.between(local, DD1CloudListing.of(2L, cloud), synced);

        assertEquals(DD1CloudPlan.Action.CONFLICT, plan.action());
        assertEquals(Arrays.asList("profile_0/persist.game.json"), plan.paths());
    }

    // A first run has no record of a sync, and a save on each side that differs
    // is not something to resolve by guessing which came first.
    @Test
    public void noRecordOfASyncWithBothSidesFullIsAConflict() {
        DD1CloudPlan plan = DD1CloudPlan.between(
            Arrays.asList(entry("profile_0/persist.game.json", "bbb")),
            DD1CloudListing.of(1L, Arrays.asList(entry("profile_0/persist.game.json", "ccc"))),
            NONE);

        assertEquals(DD1CloudPlan.Action.CONFLICT, plan.action());
    }

    @Test
    public void aFirstUploadFromAnEmptyCloudIsNotAConflict() {
        DD1CloudPlan plan = DD1CloudPlan.between(
            Arrays.asList(entry("profile_0/persist.game.json", "bbb")),
            DD1CloudListing.of(1L, NONE), NONE);

        assertEquals(DD1CloudPlan.Action.UPLOAD, plan.action());
    }

    private static DD1SaveSummary.Entry entry(String path, String sha1) {
        return new DD1SaveSummary.Entry(path, 10, 0L, sha1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1CloudPlanTest*'`
Expected: FAIL to compile, "cannot find symbol" for `DD1CloudPlan`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Which way the saves should move, or that nobody but the player can say. The
// last synced state is what makes the question answerable: without it, two
// different saves are just two different saves.
public final class DD1CloudPlan {
    public enum Action { NOTHING, UPLOAD, DOWNLOAD, CONFLICT, LOCAL_ONLY }

    private final Action action;
    private final List<String> paths;

    private DD1CloudPlan(Action action, List<String> paths) {
        this.action = action;
        this.paths = Collections.unmodifiableList(new ArrayList<>(paths));
    }

    public Action action() {
        return action;
    }

    public List<String> paths() {
        return paths;
    }

    public static DD1CloudPlan between(List<DD1SaveSummary.Entry> local,
            DD1CloudListing cloud, List<DD1SaveSummary.Entry> lastSynced) {
        // Not knowing is not the same as nothing being there, and acting on the
        // difference is how progress gets overwritten.
        if (!cloud.known()) return new DD1CloudPlan(Action.LOCAL_ONLY,
            Collections.<String>emptyList());

        List<String> localMoved = DD1SaveSummary.changed(lastSynced, local);
        List<String> cloudMoved = DD1SaveSummary.changed(lastSynced, cloud.files());
        if (localMoved.isEmpty() && cloudMoved.isEmpty())
            return new DD1CloudPlan(Action.NOTHING, Collections.<String>emptyList());
        if (cloudMoved.isEmpty()) return new DD1CloudPlan(Action.UPLOAD, localMoved);
        if (localMoved.isEmpty()) return new DD1CloudPlan(Action.DOWNLOAD, cloudMoved);

        List<String> both = new ArrayList<>(localMoved);
        for (String path : cloudMoved) {
            if (!both.contains(path)) both.add(path);
        }
        Collections.sort(both);
        return new DD1CloudPlan(Action.CONFLICT, both);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1CloudPlan.java app/src/test/java/com/winlator/dd1/DD1CloudPlanTest.java
git commit -m "feat: decide which way the saves should move, or that nobody can"
```

---

### Task 4: Reading the cloud, for real

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1CloudSaves.java`
- Modify: `app/src/main/java/com/winlator/dd1/DD1SteamSession.java` - add the
  `SteamCloud` handler beside `SteamApps` and expose it
- Modify: `app/src/main/java/com/winlator/dd1/DD1InstallService.java` - expose
  the session's cloud to callers the way `depotCatalog()` does

**Interfaces:**
- Consumes: `DD1CloudListing`, `DD1CloudTransfer`, `DD1SaveSummary`.
- Produces: `DD1CloudSaves(SteamCloud cloud)`; `DD1CloudListing list()` never
  throwing, returning `unknown()` on any failure; `byte[] fetch(String path)`
  returning null when the digest does not match or anything fails.

- [ ] **Step 1: Add the handler to the session**

In `DD1SteamSession`, beside `private final SteamApps apps = requireHandler(SteamApps.class);` add:

```java
    private final in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud cloud =
        requireHandler(in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud.class);
```

and beside `catalog()` add:

```java
    public in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud cloud() {
        return cloud;
    }
```

In `DD1InstallService`, beside `depotCatalog()` add:

```java
    public DD1CloudSaves cloudSaves() {
        return new DD1CloudSaves(steam.cloud());
    }
```

- [ ] **Step 2: Write the class**

```java
package com.winlator.dd1;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList;
import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileDownloadInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.HttpHeaders;
import in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud;

// Steam Cloud, as far as reading goes. Every failure comes back as "we do not
// know" rather than as an exception or an empty answer, because the caller's
// next move depends on telling those apart.
public final class DD1CloudSaves {
    private final SteamCloud cloud;

    public DD1CloudSaves(SteamCloud cloud) {
        this.cloud = cloud;
    }

    public DD1CloudListing list() {
        try {
            AppFileChangeList changes = cloud.getAppFileListChange(
                DD1SteamEvents.APP_ID, 0L,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
            List<String> prefixes = changes.getPathPrefixes();
            List<DD1SaveSummary.Entry> files = new ArrayList<>();
            for (AppFileInfo file : changes.getFiles()) {
                files.add(DD1CloudListing.entry(name(prefixes, file),
                    file.getRawFileSize(), file.getShaFile(),
                    file.getTimestamp() == null ? 0L : file.getTimestamp().getTime()));
            }
            return DD1CloudListing.of(changes.getCurrentChangeNumber(), files);
        }
        catch (Throwable unreadable) {
            return DD1CloudListing.unknown();
        }
    }

    // The listing was measured with no prefixes at all, but Steam may send them,
    // and then a name alone is not a path.
    private static String name(List<String> prefixes, AppFileInfo file) {
        int index = file.getPathPrefixIndex();
        if (index < 0 || index >= prefixes.size()) return file.getFilename();
        return prefixes.get(index) + file.getFilename();
    }

    public byte[] fetch(String path) {
        try {
            FileDownloadInfo info = cloud.clientFileDownload(
                DD1SteamEvents.APP_ID, path,
                in.dragonbra.javasteam.enums.ESteamRealm.SteamGlobal, false,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
            if (info.isExplicitDelete()) return null;

            byte[] body = get(DD1CloudTransfer.url(info.getUrlHost(), info.getUrlPath(),
                info.getUseHttps()), info.getRequestHeaders());
            if (body == null) return null;
            byte[] content = DD1CloudTransfer.inflate(body, info.getRawFileSize());
            // The digest Steam gave is the only reason to trust these bytes.
            if (!DD1CloudTransfer.digestMatches(content, info.getShaFile())) return null;
            return content;
        }
        catch (Throwable failed) {
            return null;
        }
    }

    private static byte[] get(String url, List<HttpHeaders> headers) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setRequestMethod("GET");
            for (HttpHeaders header : headers)
                connection.setRequestProperty(header.getName(), header.getValue());
            if (connection.getResponseCode() / 100 != 2) return null;
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                return out.toByteArray();
            }
        }
        catch (Exception failed) {
            return null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }
}
```

If `HttpHeaders` turns out to expose different accessor names, run
`javap -cp <javasteam jar> in.dragonbra.javasteam.steam.handlers.steamcloud.HttpHeaders`
and use what it prints. The jar is at
`~/.gradle/caches/modules-2/files-2.1/in.dragonbra/javasteam/1.8.0/*/javasteam-1.8.0.jar`.

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS. There is no unit test for this class: it is the seam onto
Steam, and the parts that can be tested without a network are Tasks 1 to 3.

- [ ] **Step 4: Prove it against the real cloud, on Waydroid**

The account's cloud holds exactly one file, `steam_init.json`, 166 bytes.
Fetching it exercises the listing, the URL, the HTTP GET, the inflate and the
digest check, and writes nothing.

Add this to `DD1DlcFragment.onViewCreated` temporarily:

```java
        new Thread(() -> {
            DD1CloudSaves saves = installService.cloudSaves();
            DD1CloudListing listing = saves.list();
            android.util.Log.i("DD1CLOUD", "known=" + listing.known()
                + " change=" + listing.changeNumber() + " files=" + listing.files().size());
            for (DD1SaveSummary.Entry entry : listing.files()) {
                byte[] content = saves.fetch(entry.path);
                android.util.Log.i("DD1CLOUD", entry.path + " expected=" + entry.length
                    + " got=" + (content == null ? "null" : content.length));
            }
        }).start();
```

Run it, open the content screen, and read the log:

```bash
adb -s 192.168.240.112:5555 logcat -d -s DD1CLOUD
```

Expected: `known=true`, one file, and `expected=166 got=166`. A `got=null`
means the digest check or the GET failed - find out which before going on,
because Task 5 writes.

Remove the temporary block afterwards.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1CloudSaves.java app/src/main/java/com/winlator/dd1/DD1SteamSession.java app/src/main/java/com/winlator/dd1/DD1InstallService.java
git commit -m "feat: read what Steam Cloud holds, and fetch a file from it"
```

---

### Task 5: Writing to the cloud, through one funnel

**Files:**
- Modify: `app/src/main/java/com/winlator/dd1/DD1CloudSaves.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1CloudUploadTest.java`

**Interfaces:**
- Consumes: everything above.
- Produces: `static boolean uploadable(List<DD1SaveSummary.Entry> files)` - the
  funnel's guard, false for an empty set and for any zero-length or unacceptable
  file; `boolean upload(File root, List<DD1SaveSummary.Entry> files)` performing
  the batch and returning whether every file committed.

- [ ] **Step 1: Write the failing test for the guard**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DD1CloudUploadTest {
    // Uploading nothing is how a cloud gets emptied. The funnel refuses it here
    // so no caller has to remember to.
    @Test
    public void anEmptySetIsNeverUploaded() {
        assertFalse(DD1CloudSaves.uploadable(Collections.emptyList()));
    }

    @Test
    public void aZeroLengthSaveIsNeverUploaded() {
        assertFalse(DD1CloudSaves.uploadable(Arrays.asList(
            new DD1SaveSummary.Entry("profile_0/persist.game.json", 0, 0L, "aaa"))));
    }

    @Test
    public void aPathOutOfTheTreeIsNeverUploaded() {
        assertFalse(DD1CloudSaves.uploadable(Arrays.asList(
            new DD1SaveSummary.Entry("../escape.json", 10, 0L, "aaa"))));
    }

    @Test
    public void realSavesAreUploadable() {
        assertTrue(DD1CloudSaves.uploadable(Arrays.asList(
            new DD1SaveSummary.Entry("profile_0/persist.game.json", 2140, 0L, "aaa"))));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1CloudUploadTest*'`
Expected: FAIL to compile, "cannot find symbol" for `uploadable`.

- [ ] **Step 3: Write the guard and the batch**

Add to `DD1CloudSaves`:

```java
    // The single funnel every cloud write goes through. An empty set empties the
    // cloud, and a zero-length save is what an interrupted write leaves behind;
    // both have reached other launchers' users.
    public static boolean uploadable(List<DD1SaveSummary.Entry> files) {
        if (files.isEmpty()) return false;
        for (DD1SaveSummary.Entry file : files) {
            if (file.length <= 0) return false;
            if (!DD1SaveSummary.acceptable(file)) return false;
        }
        return true;
    }

    public boolean upload(File root, List<DD1SaveSummary.Entry> files) {
        if (!uploadable(files)) return false;
        List<String> names = new ArrayList<>();
        for (DD1SaveSummary.Entry file : files) names.add(file.path);

        long batch;
        try {
            batch = cloud.beginAppUploadBatch(DD1SteamEvents.APP_ID, "DD1 Android",
                names, Collections.<String>emptyList(), 0L, 0L,
                kotlinx.coroutines.GlobalScope.INSTANCE).get().getBatchID();
        }
        catch (Exception refused) {
            return false;
        }

        boolean allDone = true;
        for (DD1SaveSummary.Entry file : files) {
            if (!send(root, file, batch)) {
                allDone = false;
                break;
            }
        }
        try {
            cloud.completeAppUploadBatch(DD1SteamEvents.APP_ID, batch,
                allDone ? in.dragonbra.javasteam.enums.EResult.OK
                    : in.dragonbra.javasteam.enums.EResult.Fail,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
        }
        catch (Exception ignored) {
            return false;
        }
        return allDone;
    }

    private boolean send(File root, DD1SaveSummary.Entry file, long batch) {
        try {
            byte[] content = read(new File(root, file.path));
            if (content == null || content.length == 0) return false;
            byte[] sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(content);

            in.dragonbra.javasteam.steam.handlers.steamcloud.FileUploadInfo info =
                cloud.beginFileUpload(DD1SteamEvents.APP_ID, content.length,
                    content.length, sha1, new java.util.Date(file.modifiedMillis),
                    file.path, 0, 0, false, false, null, batch,
                    kotlinx.coroutines.GlobalScope.INSTANCE).get();
            // Encrypted uploads need a key exchange this launcher does not do.
            // Guessing at it would write rubbish into the player's cloud.
            if (info.getEncryptFile()) return false;

            for (in.dragonbra.javasteam.steam.handlers.steamcloud.FileUploadBlockDetails block
                    : info.getBlockRequests()) {
                if (!put(block, content)) {
                    cloud.commitFileUpload(false, DD1SteamEvents.APP_ID, sha1, file.path,
                        kotlinx.coroutines.GlobalScope.INSTANCE).get();
                    return false;
                }
            }
            return cloud.commitFileUpload(true, DD1SteamEvents.APP_ID, sha1, file.path,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
        }
        catch (Exception failed) {
            return false;
        }
    }

    private static boolean put(
            in.dragonbra.javasteam.steam.handlers.steamcloud.FileUploadBlockDetails block,
            byte[] content) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(DD1CloudTransfer.url(
                block.getUrlHost(), block.getUrlPath(), block.getUseHttps())).openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            for (HttpHeaders header : block.getRequestHeaders())
                connection.setRequestProperty(header.getName(), header.getValue());
            byte[] body = block.getExplicitBodyData();
            if (body == null || body.length == 0) {
                int offset = (int)block.getBlockOffset();
                int length = Math.min(block.getBlockLength(), content.length - offset);
                body = java.util.Arrays.copyOfRange(content, offset, offset + length);
            }
            connection.getOutputStream().write(body);
            connection.getOutputStream().flush();
            return connection.getResponseCode() / 100 == 2;
        }
        catch (Exception failed) {
            return false;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] read(File file) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int got;
            while ((got = in.read(buffer)) > 0) out.write(buffer, 0, got);
            return out.toByteArray();
        }
        catch (Exception unreadable) {
            return null;
        }
    }
```

Add `import java.io.File;` and `import java.util.Collections;` to the file.

- [ ] **Step 4: Run tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Upload from the phone, download on Waydroid**

This is the experiment the whole plan is for, and the cloud holds nothing but
`steam_init.json`, so there is nothing to lose.

On the phone, add this temporarily to `DD1DlcFragment.onViewCreated`, and take it
out again afterwards:

```java
        new Thread(() -> {
            File filesDir = requireContext().getFilesDir();
            // A snapshot first, always. If it cannot be taken, nothing goes out.
            if (DD1SaveSnapshots.take(filesDir, System.currentTimeMillis()) == null) {
                android.util.Log.e("DD1CLOUD", "no snapshot, not uploading");
                return;
            }
            List<DD1SaveSummary.Entry> local =
                DD1SaveSummary.of(DD1Saves.root(filesDir));
            for (DD1SaveSummary.Entry entry : local)
                android.util.Log.i("DD1CLOUD", "local " + entry.path
                    + " " + entry.length + " " + entry.sha1);
            boolean sent = installService.cloudSaves()
                .upload(DD1Saves.root(filesDir), local);
            android.util.Log.i("DD1CLOUD", "uploaded=" + sent + " files=" + local.size());
        }).start();
```

```bash
# phone: watch it go
adb -s <phone serial> logcat -s DD1CLOUD
# waydroid: read the listing back
adb -s 192.168.240.112:5555 logcat -s DD1CLOUD
```

Expected on the phone: every file commits. Expected on Waydroid afterwards: the
listing names `profile_0/persist.game.json` with the same length and SHA-1 the
phone reported, and fetching it returns bytes whose digest matches. Compare the
digest against the phone's `DD1SaveSummary` output - equal digests are the proof
that a save crossed between two machines.

Remove the temporary wiring afterwards.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1CloudSaves.java app/src/test/java/com/winlator/dd1/DD1CloudUploadTest.java
git commit -m "feat: send saves to Steam Cloud through one guarded funnel"
```

---

## What this plan does not do

Applying a download to the save tree, and the screen that shows a conflict as
two summary cards with Keep Local and Keep Cloud, are the next plan. The pieces
they need exist after this one: `DD1CloudPlan` says which case it is,
`DD1CloudSaves.fetch` brings the bytes, `DD1SaveSnapshots` makes a wrong choice
recoverable, and `DD1SaveSummary` describes both sides in the same terms.

Nothing here writes a downloaded file into the save tree. Reading is safe and
uploading is guarded; applying is where a save gets replaced, and that deserves
its own plan and its own confirmation.

Uploading after the game exits is not here either. The design asks for a
snapshot and an upload when the saves changed during a session; this plan only
gives the pieces. Hooking it to the game exiting needs somewhere to notice that
the game exited, which the launcher does not have yet.

The last synced state has no home yet either. `DD1CloudPlan.between` takes it as
an argument, and until something records it every comparison sees an empty list -
which is why "both sides full and no record" is a conflict rather than a guess.
