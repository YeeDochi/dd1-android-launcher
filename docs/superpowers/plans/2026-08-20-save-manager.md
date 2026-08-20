# Save manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A screen that shows what each save slot holds on this device and in Steam Cloud, and moves one slot either way when the player says so.

**Architecture:** Slots, not files. Each slot is read for the three things a person recognises it by - estate name, time played, when it was saved - and the manager shows both sides in those terms. A download lands in staging, is described there, and only replaces the slot after the player has seen what they are about to lose.

**Tech Stack:** Java 8, the existing `DD1CloudSaves`, `DD1SaveSummary`, `DD1SaveSnapshots`, JUnit 4. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-19-dd1-android-launcher-design.md`, "PC save sharing", as amended by the decisions below. Earlier halves: `docs/superpowers/plans/2026-08-20-save-snapshots.md` and `docs/superpowers/plans/2026-08-20-save-cloud.md`.

## Global Constraints

Decisions that override the design doc, made 2026-08-20:

- **Both directions are manual, through this screen.** Nothing syncs at Play
  time. The design doc's "before Play, compare and apply" is not what this
  launcher does.
- **A slot at a time.** Not the whole tree. This also sidesteps the one
  unsolved problem in the cloud half: Steam's listing gives a bare filename plus
  a prefix list and `getPathPrefixIndex()` came back zero for all sixteen files,
  so the two files in the root - `persist.options.json` and `steam_init.json` -
  get named as though they were inside `profile_0`. Working slot by slot touches
  only `profile_N/` paths, where the prefix is right.
- **No verdict badge.** The screen shows both sides and the player chooses. No
  "upload recommended".

Measured on the S25 on 2026-08-20, from `profile_0/persist.game.json`:

- The file is DSON, magic `01 b1 00 00`, and its data section stores a field as
  `name\0`, padding to a four-byte boundary, then the value.
- `totalelapsed` is a float32 of seconds: 616.70703125, which is 10.3 minutes.
- `date_time` is an int32 length then that many bytes of text, including a
  trailing NUL: 20 and `2026-08-20 02:53:14\0`.
- `estatename` is the same shape: 13 and `다키스트` in UTF-8.
- A slot holds fifteen `persist.*.json` files and about 110 KB in total. Sixteen
  cloud fetches took eight seconds, so the time goes on round trips and not on
  bytes: count files, never kilobytes.

Rules carried forward:

- Snapshot before anything replaces a save, and abort if the snapshot fails.
- A downloaded file whose digest does not match Steam's is discarded, never
  written.
- An unknown cloud state is not an empty one; the screen says "unknown".
- **No real save file goes into the repository.** Tests build DSON-shaped bytes
  themselves. The launcher ships no game data and no user data.
- Every task ends green on `./gradlew assembleDebug testDebugUnitTest`.

---

### Task 1: Reading what a slot holds

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1SaveSlot.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1SaveSlotTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `DD1SaveSlot.of(File profileDir)` returning a slot or null when the
  directory holds no readable `persist.game.json`; fields `String name` (the
  directory name), `String estate`, `float playedSeconds`, `String savedAt`;
  `static String field(byte[] dson, String key)` and
  `static float number(byte[] dson, String key)` for the two shapes of value,
  both returning null / -1 when the field is not there.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DD1SaveSlotTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsTheEstateThePlaytimeAndTheSaveTime() throws IOException {
        File profile = folder.newFolder("profile_0");
        write(new File(profile, "persist.game.json"), dson());

        DD1SaveSlot slot = DD1SaveSlot.of(profile);

        assertEquals("profile_0", slot.name);
        assertEquals("Hamlet", slot.estate);
        assertEquals(616.707f, slot.playedSeconds, 0.01f);
        assertEquals("2026-08-20 02:53:14", slot.savedAt);
    }

    // A slot the player has never used has no game file, and inventing a name
    // for it would put an empty row on the screen.
    @Test
    public void anUnusedSlotIsNotASlot() throws IOException {
        assertNull(DD1SaveSlot.of(folder.newFolder("profile_3")));
    }

    // A save this cannot read is still a save: it says so rather than claiming
    // the slot is empty.
    @Test
    public void aSaveWithoutTheseFieldsStillCountsAsOne() throws IOException {
        File profile = folder.newFolder("profile_1");
        write(new File(profile, "persist.game.json"), new byte[] {1, (byte)0xb1, 0, 0, 9, 9});

        DD1SaveSlot slot = DD1SaveSlot.of(profile);

        assertEquals("profile_1", slot.name);
        assertNull(slot.estate);
        assertEquals(-1f, slot.playedSeconds, 0.01f);
        assertNull(slot.savedAt);
    }

    // Built here rather than copied from a real save: the launcher ships no game
    // data and no save data, tests included.
    private static byte[] dson() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {1, (byte)0xb1, 0, 0, 0, 0, 0, 0});
        field(out, "totalelapsed", floatBytes(616.707f));
        field(out, "estatename", text("Hamlet"));
        field(out, "date_time", text("2026-08-20 02:53:14"));
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String name, byte[] value)
            throws IOException {
        out.write(name.getBytes("UTF-8"));
        out.write(0);
        while (out.size() % 4 != 0) out.write(0);
        out.write(value);
    }

    private static byte[] floatBytes(float value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
    }

    private static byte[] text(String value) throws IOException {
        byte[] body = value.getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.allocate(4 + body.length + 1)
            .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(body.length + 1);
        buffer.put(body);
        buffer.put((byte)0);
        return buffer.array();
    }

    private static void write(File file, byte[] content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1SaveSlotTest*'`
Expected: FAIL to compile, "cannot find symbol" for `DD1SaveSlot`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// What a save slot holds, in the three terms a person recognises it by. The
// game's save is DSON, and its data section stores a field as its name, a NUL,
// padding to a four-byte boundary, and then the value - which is enough to read
// these three without understanding the rest of the format.
public final class DD1SaveSlot {
    public final String name;
    public final String estate;
    public final float playedSeconds;
    public final String savedAt;

    private DD1SaveSlot(String name, String estate, float playedSeconds, String savedAt) {
        this.name = name;
        this.estate = estate;
        this.playedSeconds = playedSeconds;
        this.savedAt = savedAt;
    }

    public static DD1SaveSlot of(File profileDir) {
        File game = new File(profileDir, "persist.game.json");
        if (!game.isFile() || game.length() == 0) return null;
        byte[] dson = read(game);
        if (dson == null) return null;
        return new DD1SaveSlot(profileDir.getName(), field(dson, "estatename"),
            number(dson, "totalelapsed"), field(dson, "date_time"));
    }

    // A length, then that many bytes of text, the last of which is a NUL.
    public static String field(byte[] dson, String key) {
        int at = valueAt(dson, key);
        if (at < 0 || at + 4 > dson.length) return null;
        int length = ByteBuffer.wrap(dson, at, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length <= 1 || at + 4 + length > dson.length) return null;
        return new String(dson, at + 4, length - 1, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static float number(byte[] dson, String key) {
        int at = valueAt(dson, key);
        if (at < 0 || at + 4 > dson.length) return -1f;
        return ByteBuffer.wrap(dson, at, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    // Scanning for the name is a heuristic, and a name that appears inside some
    // other value would mislead it. It is cheap, it only ever produces a label,
    // and a wrong read shows as a wrong label rather than as a lost save.
    private static int valueAt(byte[] dson, String key) {
        byte[] needle = (key + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i + needle.length <= dson.length; i++) {
            boolean found = true;
            for (int j = 0; j < needle.length; j++) {
                if (dson[i + j] != needle[j]) {
                    found = false;
                    break;
                }
            }
            if (!found) continue;
            int at = i + needle.length;
            while (at % 4 != 0) at++;
            return at;
        }
        return -1;
    }

    private static byte[] read(File file) {
        try (InputStream in = new FileInputStream(file)) {
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
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Check it against the real save on the phone**

The launcher has no screen for this yet, so read the numbers out with the same
arithmetic the class uses and compare:

```bash
adb -s <serial> shell "run-as com.winlator cat files/rootfs/home/xuser-1/.wine/drive_c/users/xuser/Documents/Darkest/profile_0/persist.game.json" > /tmp/game.dson
python3 - <<'EOF'
import struct
b = open('/tmp/game.dson','rb').read()
def at(key):
    i = b.index(key.encode()+b'\0') + len(key) + 1
    while i % 4: i += 1
    return i
print("played", struct.unpack('<f', b[at('totalelapsed'):at('totalelapsed')+4])[0])
j = at('date_time'); n = struct.unpack('<i', b[j:j+4])[0]
print("saved", b[j+4:j+4+n-1].decode())
EOF
```

Expected on 2026-08-20: about 616.7 seconds and `2026-08-20 02:53:14`. Whatever
it says now, the two readings must agree.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SaveSlot.java app/src/test/java/com/winlator/dd1/DD1SaveSlotTest.java
git commit -m "feat: read a save slot's estate, playtime and save time"
```

---

### Task 2: Listing the slots on both sides

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1SaveSlots.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1SaveSlotsTest.java`

**Interfaces:**
- Consumes: `DD1Saves.profiles(File)`, `DD1SaveSlot.of(File)`, `DD1CloudListing`.
- Produces: `static List<DD1SaveSlot> local(File filesDir)`;
  `static List<String> cloudSlotNames(DD1CloudListing listing)` returning the
  `profile_N` names present in a cloud listing, in slot order;
  `static List<DD1SaveSummary.Entry> filesOf(DD1CloudListing listing, String slot)`
  returning that slot's entries.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DD1SaveSlotsTest {
    @Test
    public void cloudSlotsComeFromThePathsAndSortByNumber() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Arrays.asList(
            entry("profile_10/persist.game.json"),
            entry("profile_2/persist.game.json"),
            entry("profile_2/persist.town.json"),
            entry("persist.options.json")));

        assertEquals(Arrays.asList("profile_2", "profile_10"),
            DD1SaveSlots.cloudSlotNames(listing));
    }

    @Test
    public void aSlotsFilesAreTheOnesUnderIt() {
        DD1CloudListing listing = DD1CloudListing.of(1L, Arrays.asList(
            entry("profile_2/persist.game.json"),
            entry("profile_2/persist.town.json"),
            entry("profile_3/persist.game.json")));

        List<DD1SaveSummary.Entry> files = DD1SaveSlots.filesOf(listing, "profile_2");

        assertEquals(2, files.size());
        assertEquals("profile_2/persist.game.json", files.get(0).path);
    }

    // A cloud nobody could read has no slots, and it must not look like a cloud
    // with none.
    @Test
    public void anUnknownCloudListsNothing() {
        assertEquals(Collections.emptyList(),
            DD1SaveSlots.cloudSlotNames(DD1CloudListing.unknown()));
    }

    private static DD1SaveSummary.Entry entry(String path) {
        return new DD1SaveSummary.Entry(path, 10, 0L, "aaa");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1SaveSlotsTest*'`
Expected: FAIL to compile, "cannot find symbol" for `DD1SaveSlots`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// The slots each side holds. Working a slot at a time is what keeps the two
// files Steam keeps in the root out of this entirely, and they are the ones its
// listing names wrongly.
public final class DD1SaveSlots {
    private DD1SaveSlots() {}

    public static List<DD1SaveSlot> local(File filesDir) {
        List<DD1SaveSlot> slots = new ArrayList<>();
        for (File profile : DD1Saves.profiles(DD1Saves.root(filesDir))) {
            DD1SaveSlot slot = DD1SaveSlot.of(profile);
            if (slot != null) slots.add(slot);
        }
        return slots;
    }

    public static List<String> cloudSlotNames(DD1CloudListing listing) {
        Set<String> names = new LinkedHashSet<>();
        for (DD1SaveSummary.Entry file : listing.files()) {
            String slot = slotOf(file.path);
            if (slot != null) names.add(slot);
        }
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Integer.compare(DD1Saves.slotOf(left), DD1Saves.slotOf(right));
            }
        });
        return sorted;
    }

    public static List<DD1SaveSummary.Entry> filesOf(DD1CloudListing listing, String slot) {
        List<DD1SaveSummary.Entry> files = new ArrayList<>();
        for (DD1SaveSummary.Entry file : listing.files()) {
            if (slot.equals(slotOf(file.path))) files.add(file);
        }
        return files;
    }

    // Anything not under a profile_N is not part of a slot - the options file and
    // Steam's own marker both live in the root.
    private static String slotOf(String path) {
        int slash = path.indexOf('/');
        if (slash <= 0) return null;
        String head = path.substring(0, slash);
        return DD1Saves.slotOf(head) < 0 ? null : head;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SaveSlots.java app/src/test/java/com/winlator/dd1/DD1SaveSlotsTest.java
git commit -m "feat: list the save slots this device and the cloud hold"
```

---

### Task 3: Staging a slot out of the cloud

**Files:**
- Modify: `app/src/main/java/com/winlator/dd1/DD1CloudSaves.java`
- Create: `app/src/main/java/com/winlator/dd1/DD1SaveStaging.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1SaveStagingTest.java`

**Interfaces:**
- Consumes: `DD1SaveSummary.Entry`, `DD1SaveSlot`, `DD1SaveSnapshots`.
- Produces: `static File dir(File filesDir, String slot)` at
  `staging/saves/<slot>`; `static void clear(File filesDir, String slot)`;
  `static boolean put(File filesDir, String slot, String path, byte[] content)`
  writing one file into staging, refusing an empty one and a path outside the
  slot; `static DD1SaveSlot describe(File filesDir, String slot)`;
  `static boolean apply(File filesDir, String slot)` snapshotting, then replacing
  the slot in the save tree with what is staged.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DD1SaveStagingTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aStagedFileLandsUnderTheSlot() throws IOException {
        File files = folder.newFolder();

        assertTrue(DD1SaveStaging.put(files, "profile_0",
            "profile_0/persist.game.json", new byte[] {1, 2, 3}));

        assertEquals(3,
            new File(files, "staging/saves/profile_0/persist.game.json").length());
    }

    // An empty file is what a transfer that failed leaves behind, and it must
    // never reach the point where it could replace a save.
    @Test
    public void anEmptyFileIsRefused() throws IOException {
        File files = folder.newFolder();

        assertFalse(DD1SaveStaging.put(files, "profile_0",
            "profile_0/persist.game.json", new byte[0]));
    }

    @Test
    public void aPathOutsideTheSlotIsRefused() throws IOException {
        File files = folder.newFolder();

        assertFalse(DD1SaveStaging.put(files, "profile_0",
            "profile_1/persist.game.json", new byte[] {1}));
        assertFalse(DD1SaveStaging.put(files, "profile_0",
            "profile_0/../escape.json", new byte[] {1}));
    }

    // Replacing a slot is the one step that can lose a campaign, so it takes a
    // snapshot first and gives up if it cannot have one.
    @Test
    public void applyingReplacesTheSlotAndSnapshotsFirst() throws IOException {
        File files = folder.newFolder();
        File live = new File(DD1Saves.root(files), "profile_0");
        live.mkdirs();
        write(new File(live, "persist.game.json"), "old save".getBytes());
        write(new File(live, "persist.stale.json"), "gone".getBytes());
        DD1SaveStaging.put(files, "profile_0", "profile_0/persist.game.json",
            "new save".getBytes());

        assertTrue(DD1SaveStaging.apply(files, "profile_0"));

        assertEquals(8, new File(live, "persist.game.json").length());
        assertFalse(new File(live, "persist.stale.json").exists());
        assertEquals(1, DD1SaveSnapshots.kept(files).size());
    }

    @Test
    public void applyingNothingIsRefused() throws IOException {
        File files = folder.newFolder();
        File live = new File(DD1Saves.root(files), "profile_0");
        live.mkdirs();
        write(new File(live, "persist.game.json"), "old save".getBytes());

        assertFalse(DD1SaveStaging.apply(files, "profile_0"));

        assertEquals(8, new File(live, "persist.game.json").length());
    }

    private static void write(File file, byte[] content) throws IOException {
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1SaveStagingTest*'`
Expected: FAIL to compile, "cannot find symbol" for `DD1SaveStaging`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

// A slot fetched out of the cloud waits here until the player has seen what it
// holds. Nothing in the save tree moves until they say so, and then only after a
// snapshot - the same shape as the game's own staging directory, for the same
// reason.
public final class DD1SaveStaging {
    private DD1SaveStaging() {}

    public static File dir(File filesDir, String slot) {
        return new File(filesDir, "staging/saves/" + slot);
    }

    public static void clear(File filesDir, String slot) {
        delete(dir(filesDir, slot));
    }

    public static boolean put(File filesDir, String slot, String path, byte[] content) {
        if (content == null || content.length == 0) return false;
        if (!path.startsWith(slot + "/")) return false;
        String inside = path.substring(slot.length() + 1);
        if (inside.isEmpty() || inside.contains("..") || inside.startsWith("/")) return false;

        File target = new File(dir(filesDir, slot), inside);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(content);
            return true;
        }
        catch (Exception failed) {
            return false;
        }
    }

    public static DD1SaveSlot describe(File filesDir, String slot) {
        return DD1SaveSlot.of(dir(filesDir, slot));
    }

    public static boolean apply(File filesDir, String slot) {
        File staged = dir(filesDir, slot);
        // A staged slot with no game file in it is not a save, and replacing a
        // real one with it would be the whole disaster in one step.
        if (DD1SaveSlot.of(staged) == null) return false;
        if (DD1SaveSnapshots.take(filesDir, System.currentTimeMillis()) == null) return false;

        File live = new File(DD1Saves.root(filesDir), slot);
        delete(live);
        if (!live.getParentFile().exists() && !live.getParentFile().mkdirs()) return false;
        if (!staged.renameTo(live)) return false;
        return true;
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS. `applyingReplacesTheSlotAndSnapshotsFirst` needs the snapshot to
succeed, which it does because the live slot is a save tree before the swap.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SaveStaging.java app/src/test/java/com/winlator/dd1/DD1SaveStagingTest.java
git commit -m "feat: stage a slot out of the cloud before it replaces anything"
```

---

### Task 4: The screen

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1SavesFragment.java`
- Create: `app/src/main/res/layout/dd1_saves_fragment.xml`
- Create: `app/src/main/res/layout/dd1_save_slot_row.xml`
- Modify: `app/src/main/res/layout/dd1_activity.xml` - a drawer entry
- Modify: `app/src/main/java/com/winlator/dd1/DD1Activity.java` - push the screen
- Modify: `app/src/main/res/values/dd1_strings.xml` and
  `app/src/main/res/values-ko/dd1_strings.xml`

**Interfaces:**
- Consumes: `DD1SaveSlots`, `DD1SaveSlot`, `DD1SaveStaging`, `DD1CloudSaves`,
  `DD1SaveSnapshots`, `DD1InstallService.cloudSaves()`.
- Produces: a drawer destination. Nothing else depends on it.

- [ ] **Step 1: Add the strings**

To `app/src/main/res/values/dd1_strings.xml`:

```xml
    <string name="dd1_saves">Saves</string>
    <string name="dd1_saves_local">On this device</string>
    <string name="dd1_saves_cloud">Steam Cloud</string>
    <string name="dd1_saves_cloud_unknown">Steam has not answered yet.</string>
    <string name="dd1_saves_none">No saves here.</string>
    <string name="dd1_saves_slot">%1$s · %2$s · %3$s</string>
    <string name="dd1_saves_played">%.1f min</string>
    <string name="dd1_saves_upload">Send to Steam Cloud</string>
    <string name="dd1_saves_download">Get from Steam Cloud</string>
    <string name="dd1_saves_replace_title">Replace this save</string>
    <string name="dd1_saves_replace">%1$s becomes %2$s. The save on this device is snapshotted first.</string>
    <string name="dd1_saves_progress">%1$d/%2$d files</string>
    <string name="dd1_saves_snapshots">Snapshots</string>
```

To `app/src/main/res/values-ko/dd1_strings.xml`:

```xml
    <string name="dd1_saves">세이브</string>
    <string name="dd1_saves_local">이 기기</string>
    <string name="dd1_saves_cloud">Steam 클라우드</string>
    <string name="dd1_saves_cloud_unknown">Steam이 아직 응답하지 않았습니다.</string>
    <string name="dd1_saves_none">세이브가 없습니다.</string>
    <string name="dd1_saves_slot">%1$s · %2$s · %3$s</string>
    <string name="dd1_saves_played">%.1f분</string>
    <string name="dd1_saves_upload">클라우드로 보내기</string>
    <string name="dd1_saves_download">클라우드에서 가져오기</string>
    <string name="dd1_saves_replace_title">이 세이브를 바꿉니다</string>
    <string name="dd1_saves_replace">%1$s이(가) %2$s로 바뀝니다. 이 기기의 세이브는 먼저 스냅샷으로 남습니다.</string>
    <string name="dd1_saves_progress">%1$d/%2$d 파일</string>
    <string name="dd1_saves_snapshots">스냅샷</string>
```

- [ ] **Step 2: Add the drawer entry**

In `dd1_activity.xml`, after the `BTDrawerDlc` button and before
`BTDrawerSettings`, replacing the comment that marks the place:

```xml
        <Button
            android:id="@+id/BTDrawerSaves"
            style="?android:attr/borderlessButtonStyle"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:gravity="center_vertical|start"
            android:paddingStart="16dp"
            android:text="@string/dd1_saves"
            android:textColor="?attr/colorPrimaryText" />
```

In `DD1Activity.onCreate`, beside the other two:

```java
        findViewById(R.id.BTDrawerSaves).setOnClickListener(v -> {
            closeDrawer();
            showScreen(new DD1SavesFragment());
        });
```

- [ ] **Step 3: Write the row layout**

`app/src/main/res/layout/dd1_save_slot_row.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingTop="8dp"
    android:paddingBottom="8dp">

    <TextView
        android:id="@+id/TVSlot"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textColor="?attr/colorPrimaryText"
        android:textSize="15sp" />

    <Button
        android:id="@+id/BTSlotAction"
        style="?android:attr/buttonStyleSmall"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</LinearLayout>
```

- [ ] **Step 4: Write the screen layout**

`app/src/main/res/layout/dd1_saves_fragment.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="16dp"
    android:paddingTop="12dp"
    android:paddingEnd="16dp"
    android:paddingBottom="12dp">

    <include layout="@layout/dd1_screen_header" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="8dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/dd1_saves_local"
                android:textColor="?attr/colorPrimaryText"
                android:textStyle="bold" />

            <LinearLayout
                android:id="@+id/LLLocalSlots"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:text="@string/dd1_saves_cloud"
                android:textColor="?attr/colorPrimaryText"
                android:textStyle="bold" />

            <ProgressBar
                android:id="@+id/PBSavesLoading"
                style="?android:attr/progressBarStyleLarge"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center_horizontal"
                android:layout_marginTop="12dp"
                android:visibility="gone" />

            <LinearLayout
                android:id="@+id/LLCloudSlots"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <TextView
                android:id="@+id/TVSavesStatus"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:textColor="?attr/colorSecondaryText"
                android:textSize="12sp" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:text="@string/dd1_saves_snapshots"
                android:textColor="?attr/colorPrimaryText"
                android:textStyle="bold" />

            <LinearLayout
                android:id="@+id/LLSnapshots"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />
        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 5: Write the fragment**

```java
package com.winlator.dd1;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.winlator.R;

import java.io.File;
import java.util.List;
import java.util.Locale;

// What each side holds, slot by slot, and a way to move one. Nothing here
// decides anything: it shows the estate, the time played and when it was saved,
// and the player chooses.
public class DD1SavesFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private DD1InstallService installService;
    private boolean serviceBound;
    private DD1CloudListing cloud = DD1CloudListing.unknown();
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            installService = ((DD1InstallService.LocalBinder)binder).getService();
            serviceBound = true;
            loadCloud();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            installService = null;
            serviceBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dd1_saves_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView)view.findViewById(R.id.TVScreenTitle)).setText(R.string.dd1_saves);
        view.findViewById(R.id.BTScreenBack).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        requireContext().bindService(new Intent(requireContext(), DD1InstallService.class),
            serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        if (serviceBound) {
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
            installService = null;
        }
        super.onStop();
    }

    // The listing fails until Steam has signed the account in, and that is not
    // the same as the cloud being empty, so it is retried rather than believed.
    private void loadCloud() {
        View view = getView();
        if (view != null) view.findViewById(R.id.PBSavesLoading).setVisibility(View.VISIBLE);
        new Thread(() -> {
            DD1CloudSaves saves = installService == null ? null : installService.cloudSaves();
            DD1CloudListing listing = DD1CloudListing.unknown();
            for (int i = 0; saves != null && i < 10 && !listing.known(); i++) {
                listing = saves.list();
                if (!listing.known()) {
                    try {
                        Thread.sleep(3000L);
                    }
                    catch (InterruptedException stop) {
                        return;
                    }
                }
            }
            final DD1CloudListing answer = listing;
            main.post(() -> {
                cloud = answer;
                View current = getView();
                if (current != null)
                    current.findViewById(R.id.PBSavesLoading).setVisibility(View.GONE);
                render();
            });
        }).start();
    }

    private void render() {
        View view = getView();
        if (view == null) return;
        File filesDir = requireContext().getFilesDir();

        LinearLayout localList = view.findViewById(R.id.LLLocalSlots);
        localList.removeAllViews();
        List<DD1SaveSlot> local = DD1SaveSlots.local(filesDir);
        if (local.isEmpty()) localList.addView(note(R.string.dd1_saves_none));
        for (DD1SaveSlot slot : local)
            localList.addView(row(slot, R.string.dd1_saves_upload, v -> upload(slot)));

        LinearLayout cloudList = view.findViewById(R.id.LLCloudSlots);
        cloudList.removeAllViews();
        if (!cloud.known()) cloudList.addView(note(R.string.dd1_saves_cloud_unknown));
        else {
            List<String> names = DD1SaveSlots.cloudSlotNames(cloud);
            if (names.isEmpty()) cloudList.addView(note(R.string.dd1_saves_none));
            for (String name : names) cloudList.addView(cloudRow(name));
        }

        LinearLayout snapshots = view.findViewById(R.id.LLSnapshots);
        snapshots.removeAllViews();
        for (File snapshot : DD1SaveSnapshots.kept(filesDir)) {
            TextView line = note(0);
            line.setText(new java.util.Date(Long.parseLong(snapshot.getName())).toString());
            snapshots.addView(line);
        }
    }

    private TextView note(int text) {
        TextView view = new TextView(requireContext());
        if (text != 0) view.setText(text);
        return view;
    }

    private View row(DD1SaveSlot slot, int action, View.OnClickListener onClick) {
        View row = LayoutInflater.from(requireContext())
            .inflate(R.layout.dd1_save_slot_row, null, false);
        ((TextView)row.findViewById(R.id.TVSlot)).setText(describe(slot));
        Button button = row.findViewById(R.id.BTSlotAction);
        button.setText(action);
        button.setOnClickListener(onClick);
        return row;
    }

    private View cloudRow(String name) {
        View row = LayoutInflater.from(requireContext())
            .inflate(R.layout.dd1_save_slot_row, null, false);
        ((TextView)row.findViewById(R.id.TVSlot)).setText(name);
        Button button = row.findViewById(R.id.BTSlotAction);
        button.setText(R.string.dd1_saves_download);
        button.setOnClickListener(v -> download(name));
        return row;
    }

    private String describe(DD1SaveSlot slot) {
        String played = slot.playedSeconds < 0 ? "?"
            : String.format(Locale.US, getString(R.string.dd1_saves_played),
                slot.playedSeconds / 60f);
        return getString(R.string.dd1_saves_slot, slot.name,
            slot.estate == null ? slot.name : slot.estate,
            played + (slot.savedAt == null ? "" : " · " + slot.savedAt));
    }

    private void status(int done, int total) {
        View view = getView();
        if (view == null) return;
        ((TextView)view.findViewById(R.id.TVSavesStatus))
            .setText(getString(R.string.dd1_saves_progress, done, total));
    }

    private void upload(DD1SaveSlot slot) {
        File filesDir = requireContext().getFilesDir();
        List<DD1SaveSummary.Entry> files = DD1SaveSummary.of(
            new File(DD1Saves.root(filesDir), slot.name));
        // Paths go up as the cloud will hold them, under the slot.
        java.util.List<DD1SaveSummary.Entry> named = new java.util.ArrayList<>();
        for (DD1SaveSummary.Entry file : files)
            named.add(new DD1SaveSummary.Entry(slot.name + "/" + file.path, file.length,
                file.modifiedMillis, file.sha1));

        new Thread(() -> {
            if (DD1SaveSnapshots.take(filesDir, System.currentTimeMillis()) == null) return;
            boolean sent = installService != null
                && installService.cloudSaves().upload(DD1Saves.root(filesDir), named);
            final int count = named.size();
            main.post(() -> {
                status(sent ? count : 0, count);
                loadCloud();
            });
        }).start();
    }

    private void download(String slot) {
        File filesDir = requireContext().getFilesDir();
        List<DD1SaveSummary.Entry> files = DD1SaveSlots.filesOf(cloud, slot);
        new Thread(() -> {
            DD1SaveStaging.clear(filesDir, slot);
            int done = 0;
            for (DD1SaveSummary.Entry file : files) {
                byte[] content = installService == null ? null
                    : installService.cloudSaves().fetch(file.path);
                if (content == null || !DD1SaveStaging.put(filesDir, slot, file.path, content))
                    break;
                done++;
                final int sofar = done;
                main.post(() -> status(sofar, files.size()));
            }
            final boolean whole = done == files.size();
            main.post(() -> {
                if (whole) confirmReplace(slot);
                else DD1SaveStaging.clear(filesDir, slot);
            });
        }).start();
    }

    // The staged slot is described before anything is replaced, so the choice is
    // made against what the cloud actually holds rather than against its name.
    private void confirmReplace(String slot) {
        File filesDir = requireContext().getFilesDir();
        DD1SaveSlot staged = DD1SaveStaging.describe(filesDir, slot);
        if (staged == null) {
            DD1SaveStaging.clear(filesDir, slot);
            return;
        }
        DD1SaveSlot live = DD1SaveSlot.of(new File(DD1Saves.root(filesDir), slot));
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_saves_replace_title)
            .setMessage(getString(R.string.dd1_saves_replace,
                live == null ? slot : describe(live), describe(staged)))
            .setNegativeButton(android.R.string.cancel,
                (dialog, which) -> DD1SaveStaging.clear(filesDir, slot))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                DD1SaveStaging.apply(filesDir, slot);
                render();
            })
            .show();
    }
}
```

- [ ] **Step 6: Build and run the suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Check it on Waydroid, which has no saves**

```bash
adb -s 192.168.240.112:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.240.112:5555 shell am start -n com.winlator/com.winlator.dd1.DD1Activity
```

Open the drawer, choose the saves entry. Expected: "On this device" says there
are none, the cloud section shows a spinner and then `profile_0`, and the
snapshot list is empty. Then press the cloud slot's button: the files come down,
the progress line counts to fifteen, and the dialog names the staged save's
estate, playtime and save time. Cancel it - Waydroid has no game to play the
save with.

- [ ] **Step 8: Check it on the phone, which has the real campaign**

Expected: the local slot reads `profile_0 · 다키스트 · 10.3 min · 2026-08-20
02:53:14` or whatever the campaign has reached, and the cloud slot reads the
same. Send it up, and confirm the cloud row still matches. Then get it back and
let the dialog appear, confirm, and check the game still starts and the campaign
is where it was.

```bash
adb -s <serial> shell "run-as com.winlator ls files/snapshots/saves"
```

Expected: a new snapshot for each transfer that touched the tree.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SavesFragment.java app/src/main/res/layout/dd1_saves_fragment.xml app/src/main/res/layout/dd1_save_slot_row.xml app/src/main/res/layout/dd1_activity.xml app/src/main/java/com/winlator/dd1/DD1Activity.java app/src/main/res/values/dd1_strings.xml app/src/main/res/values-ko/dd1_strings.xml
git commit -m "feat: a save manager that shows both sides and moves one slot"
```

---

## What this plan does not do

The last synced state still has nowhere to live, so `DD1CloudPlan` is not used
here: the screen shows both sides rather than a verdict. That is the decision,
not an omission - but it also means the comparison code written in the cloud plan
has no caller yet.

Steam's listing still names the two files in the root as though they were inside
`profile_0`. Working slot by slot keeps them out of every transfer, so the bug
cannot bite, but it is not fixed. `persist.options.json` holds the game's own
settings and no slot's progress, so nothing here needs it.

Restoring a snapshot is listed but not wired. The snapshots are shown so the
player can see they exist; putting one back is the same act as applying a staged
slot and can reuse it once there is a screen to choose from.
