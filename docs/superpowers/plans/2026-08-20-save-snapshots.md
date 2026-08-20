# Save snapshots and summaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Read the game's saves out of the Wine prefix, describe them well enough to compare, and keep three snapshots so a save can never be lost to whatever comes next.

**Architecture:** Three pure units over the filesystem - locating and recognising a save tree, summarising it file by file, and copying it into a bounded ring of snapshots. Nothing here talks to Steam; cloud transfer is the next plan and consumes the summaries this one produces.

**Tech Stack:** Java 8, JUnit 4 with `TemporaryFolder`, `java.security.MessageDigest` for SHA-1. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-19-dd1-android-launcher-design.md`, the "PC save sharing" section, and `docs/superpowers/specs/2026-08-19-dd1-launcher-features.md`, "Save location".

## Global Constraints

- Saves live at `files/rootfs/home/xuser-1/.wine/drive_c/users/xuser/Documents/Darkest`, measured on the S25 on 2026-08-20. Below it are `profile_0` through `profile_9` and `persist.options.json`; a used profile holds fifteen `persist.*.json` files.
- The design's mapping of the save directory to `files/saves` is deliberately **not** done: `activate()` renames `game/` only, so saves already survive an install, and moving a live campaign is a risk with nothing to buy. Revisit when the launcher offers to reset the prefix.
- A recognisable save tree contains a `profile_<number>` directory holding a non-empty `persist.game.json`.
- A summary entry is relative path, byte length, modification time in milliseconds, and SHA-1.
- Keep the latest three snapshots.
- Reject absolute paths, `..` traversal, and files larger than 100 MiB (Steam's per-file limit).
- Nothing in this plan deletes or overwrites a save. Snapshots only copy.
- Every task ends green on `./gradlew assembleDebug testDebugUnitTest`.

---

### Task 1: Recognising a save tree

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1Saves.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1SavesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `static File root(File filesDir)` returning the save directory whether or not it exists; `static boolean isSaveTree(File root)`; `static List<File> profiles(File root)` returning the `profile_<n>` directories that hold a non-empty `persist.game.json`, ordered by profile number.

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
import java.util.List;

public class DD1SavesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theSaveRootIsInsideTheWinePrefix() throws IOException {
        File files = folder.newFolder();

        assertEquals(new File(files,
                "rootfs/home/xuser-1/.wine/drive_c/users/xuser/Documents/Darkest"),
            DD1Saves.root(files));
    }

    // An empty profile_0 is what a prefix that has never run the game looks
    // like, and calling that a save would offer to snapshot nothing.
    @Test
    public void aProfileWithoutAGameFileIsNotASave() throws IOException {
        File files = folder.newFolder();
        new File(DD1Saves.root(files), "profile_0").mkdirs();

        assertFalse(DD1Saves.isSaveTree(DD1Saves.root(files)));
        assertEquals(0, DD1Saves.profiles(DD1Saves.root(files)).size());
    }

    @Test
    public void aProfileWithAGameFileIsASave() throws IOException {
        File files = folder.newFolder();
        profile(files, 0, "{\"x\":1}");

        assertTrue(DD1Saves.isSaveTree(DD1Saves.root(files)));
        List<File> profiles = DD1Saves.profiles(DD1Saves.root(files));
        assertEquals(1, profiles.size());
        assertEquals("profile_0", profiles.get(0).getName());
    }

    @Test
    public void anEmptyGameFileIsNotASave() throws IOException {
        File files = folder.newFolder();
        profile(files, 0, "");

        assertFalse(DD1Saves.isSaveTree(DD1Saves.root(files)));
    }

    // The game numbers its slots and the screen will list them, so ten sorts
    // after two rather than between one and three.
    @Test
    public void profilesComeBackInSlotOrder() throws IOException {
        File files = folder.newFolder();
        profile(files, 10, "{}");
        profile(files, 2, "{}");
        profile(files, 1, "{}");

        List<File> profiles = DD1Saves.profiles(DD1Saves.root(files));

        assertEquals("profile_1", profiles.get(0).getName());
        assertEquals("profile_2", profiles.get(1).getName());
        assertEquals("profile_10", profiles.get(2).getName());
    }

    @Test
    public void aMissingRootIsNotAnError() throws IOException {
        File files = folder.newFolder();

        assertFalse(DD1Saves.isSaveTree(DD1Saves.root(files)));
        assertEquals(0, DD1Saves.profiles(DD1Saves.root(files)).size());
    }

    private static void profile(File files, int slot, String game) throws IOException {
        File dir = new File(DD1Saves.root(files), "profile_" + slot);
        dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir, "persist.game.json"))) {
            out.write(game.getBytes("UTF-8"));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1SavesTest*'`
Expected: FAIL to compile with "cannot find symbol" for `DD1Saves`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Where the game keeps its progress, and whether what is there is progress at
// all. The path is inside the Wine prefix rather than beside the game, because
// that is where Wine puts the user's Documents directory and where the game
// looks.
public final class DD1Saves {
    private DD1Saves() {}

    private static final String PREFIX_PATH =
        "rootfs/home/xuser-1/.wine/drive_c/users/xuser/Documents/Darkest";

    public static File root(File filesDir) {
        return new File(filesDir, PREFIX_PATH);
    }

    public static boolean isSaveTree(File root) {
        return !profiles(root).isEmpty();
    }

    // A slot the player has never used is an empty directory, and the game
    // writes persist.game.json first, so that file is what makes a slot a save.
    public static List<File> profiles(File root) {
        List<File> found = new ArrayList<>();
        File[] entries = root.listFiles();
        if (entries == null) return found;
        for (File entry : entries) {
            if (slotOf(entry.getName()) < 0) continue;
            File game = new File(entry, "persist.game.json");
            if (game.isFile() && game.length() > 0) found.add(entry);
        }
        Collections.sort(found, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Integer.compare(slotOf(left.getName()), slotOf(right.getName()));
            }
        });
        return found;
    }

    // "profile_10" is a slot; anything else in the directory is not.
    static int slotOf(String name) {
        if (!name.startsWith("profile_")) return -1;
        try {
            return Integer.parseInt(name.substring("profile_".length()));
        }
        catch (NumberFormatException notASlot) {
            return -1;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS, no failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1Saves.java app/src/test/java/com/winlator/dd1/DD1SavesTest.java
git commit -m "feat: find the game's saves and tell a used slot from an empty one"
```

---

### Task 2: Summarising a save tree

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1SaveSummary.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1SaveSummaryTest.java`

**Interfaces:**
- Consumes: `DD1Saves.root(File)` from Task 1.
- Produces: `DD1SaveSummary.Entry` with public final `String path`, `long length`, `long modifiedMillis`, `String sha1`; `static List<Entry> of(File root)` walking the tree; `static List<String> changed(List<Entry> before, List<Entry> after)` naming the paths whose SHA-1 differs or which appear or disappear; `static boolean acceptable(Entry entry)` applying the path and size rules.

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
import java.util.Arrays;
import java.util.List;

public class DD1SaveSummaryTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void describesEveryFileByPathLengthAndDigest() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");

        List<DD1SaveSummary.Entry> entries = DD1SaveSummary.of(root);

        assertEquals(1, entries.size());
        assertEquals("profile_0/persist.game.json", entries.get(0).path);
        assertEquals(3, entries.get(0).length);
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", entries.get(0).sha1);
    }

    // The same bytes written twice must read as unchanged, or every launch would
    // look like a save worth uploading.
    @Test
    public void identicalContentIsNotAChange() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");
        List<DD1SaveSummary.Entry> before = DD1SaveSummary.of(root);
        write(root, "profile_0/persist.game.json", "abc");

        assertEquals(0, DD1SaveSummary.changed(before, DD1SaveSummary.of(root)).size());
    }

    @Test
    public void editedAddedAndRemovedFilesAreAllChanges() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");
        write(root, "profile_0/persist.town.json", "keep");
        List<DD1SaveSummary.Entry> before = DD1SaveSummary.of(root);

        write(root, "profile_0/persist.game.json", "different");
        write(root, "profile_0/persist.roster.json", "new");
        new File(root, "profile_0/persist.town.json").delete();

        List<String> changed = DD1SaveSummary.changed(before, DD1SaveSummary.of(root));

        assertEquals(Arrays.asList(
            "profile_0/persist.game.json",
            "profile_0/persist.roster.json",
            "profile_0/persist.town.json"), changed);
    }

    @Test
    public void pathsAreRelativeAndUseForwardSlashes() throws IOException {
        File root = folder.newFolder("Darkest");
        write(root, "profile_0/persist.game.json", "abc");

        String path = DD1SaveSummary.of(root).get(0).path;

        assertFalse(path.startsWith("/"));
        assertFalse(path.contains("\\"));
    }

    @Test
    public void aPathThatClimbsOutOfTheTreeIsRefused() {
        assertFalse(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("../persist.game.json", 3, 0, "x")));
        assertFalse(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("/etc/passwd", 3, 0, "x")));
        assertTrue(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("profile_0/persist.game.json", 3, 0, "x")));
    }

    // Steam refuses anything larger, so a file that big is not a save Steam will
    // ever hold and sending it would fail the whole batch.
    @Test
    public void aFileOverAHundredMebibytesIsRefused() {
        assertFalse(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("profile_0/big.json", 100L * 1024 * 1024 + 1, 0, "x")));
        assertTrue(DD1SaveSummary.acceptable(
            new DD1SaveSummary.Entry("profile_0/big.json", 100L * 1024 * 1024, 0, "x")));
    }

    @Test
    public void aMissingTreeSummarisesToNothing() throws IOException {
        assertEquals(0, DD1SaveSummary.of(new File(folder.newFolder(), "absent")).size());
    }

    private static void write(File root, String path, String text) throws IOException {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1SaveSummaryTest*'`
Expected: FAIL to compile with "cannot find symbol" for `DD1SaveSummary`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// What a save tree looks like, in enough detail to tell one state from another
// without reading the files again. Steam Cloud compares the same four things.
public final class DD1SaveSummary {
    private DD1SaveSummary() {}

    private static final long MAX_BYTES = 100L * 1024 * 1024;

    public static final class Entry {
        public final String path;
        public final long length;
        public final long modifiedMillis;
        public final String sha1;

        public Entry(String path, long length, long modifiedMillis, String sha1) {
            this.path = path;
            this.length = length;
            this.modifiedMillis = modifiedMillis;
            this.sha1 = sha1;
        }
    }

    public static List<Entry> of(File root) {
        List<Entry> entries = new ArrayList<>();
        collect(root, "", entries);
        Collections.sort(entries, (left, right) -> left.path.compareTo(right.path));
        return entries;
    }

    private static void collect(File file, String prefix, List<Entry> entries) {
        File[] children = file.listFiles();
        if (children == null) return;
        for (File child : children) {
            String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
            if (child.isDirectory()) collect(child, path, entries);
            else entries.add(new Entry(path, child.length(), child.lastModified(), sha1(child)));
        }
    }

    // Named rather than counted: the caller says which files moved, so a log can
    // name them and an upload can send only those.
    public static List<String> changed(List<Entry> before, List<Entry> after) {
        Map<String, String> was = digests(before);
        Map<String, String> is = digests(after);
        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(was.keySet());
        paths.addAll(is.keySet());
        List<String> changed = new ArrayList<>();
        for (String path : paths) {
            String left = was.get(path);
            String right = is.get(path);
            if (left == null || right == null || !left.equals(right)) changed.add(path);
        }
        return changed;
    }

    private static Map<String, String> digests(List<Entry> entries) {
        Map<String, String> digests = new LinkedHashMap<>();
        for (Entry entry : entries) digests.put(entry.path, entry.sha1);
        return digests;
    }

    // A path out of the tree, or a file Steam will not hold, has no business in
    // a transfer either way.
    public static boolean acceptable(Entry entry) {
        if (entry.path.isEmpty()) return false;
        if (entry.path.startsWith("/") || entry.path.contains("\\")) return false;
        for (String part : entry.path.split("/")) {
            if (part.equals("..")) return false;
        }
        return entry.length <= MAX_BYTES;
    }

    private static String sha1(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (InputStream in = new FileInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            StringBuilder text = new StringBuilder();
            for (byte value : digest.digest()) text.append(String.format("%02x", value));
            return text.toString();
        }
        catch (NoSuchAlgorithmException | IOException unreadable) {
            // An unreadable file is not the same as an empty one, and must not
            // compare equal to anything.
            return "unreadable:" + file.getName() + ":" + file.lastModified();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS, no failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SaveSummary.java app/src/test/java/com/winlator/dd1/DD1SaveSummaryTest.java
git commit -m "feat: describe a save tree well enough to compare it"
```

---

### Task 3: Keeping three snapshots

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1SaveSnapshots.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1SaveSnapshotsTest.java`

**Interfaces:**
- Consumes: `DD1Saves.root(File)` and `DD1Saves.isSaveTree(File)` from Task 1.
- Produces: `static File take(File filesDir, long atMillis)` copying the save tree to `snapshots/saves/<atMillis>` and returning it, or `null` when there is no save to take; `static List<File> kept(File filesDir)` newest first; `static final int KEPT = 3`.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class DD1SaveSnapshotsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aSnapshotCopiesTheSaveWithoutTouchingIt() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        File snapshot = DD1SaveSnapshots.take(files, 1000L);

        assertEquals("1000", snapshot.getName());
        assertTrue(new File(snapshot, "profile_0/persist.game.json").isFile());
        assertTrue(new File(DD1Saves.root(files), "profile_0/persist.game.json").isFile());
    }

    @Test
    public void thereIsNothingToSnapshotBeforeTheGameHasSaved() throws IOException {
        assertNull(DD1SaveSnapshots.take(folder.newFolder(), 1000L));
    }

    // Three is the whole point of the ring: the fourth pushes the first out
    // rather than filling the phone.
    @Test
    public void onlyTheLatestThreeAreKept() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        DD1SaveSnapshots.take(files, 1000L);
        DD1SaveSnapshots.take(files, 2000L);
        DD1SaveSnapshots.take(files, 3000L);
        DD1SaveSnapshots.take(files, 4000L);

        List<File> kept = DD1SaveSnapshots.kept(files);
        assertEquals(3, kept.size());
        assertEquals("4000", kept.get(0).getName());
        assertEquals("3000", kept.get(1).getName());
        assertEquals("2000", kept.get(2).getName());
    }

    @Test
    public void takingTheSameMomentTwiceReplacesRatherThanDuplicates() throws IOException {
        File files = folder.newFolder();
        save(files, "first");
        DD1SaveSnapshots.take(files, 1000L);
        save(files, "second");

        File snapshot = DD1SaveSnapshots.take(files, 1000L);

        assertEquals(1, DD1SaveSnapshots.kept(files).size());
        assertEquals(6, new File(snapshot, "profile_0/persist.game.json").length());
    }

    private static void save(File files, String content) throws IOException {
        File dir = new File(DD1Saves.root(files), "profile_0");
        dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir, "persist.game.json"))) {
            out.write(content.getBytes("UTF-8"));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1SaveSnapshotsTest*'`
Expected: FAIL to compile with "cannot find symbol" for `DD1SaveSnapshots`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// A copy of the saves taken before anything is allowed to change them. Three are
// kept, because the point is to be able to go back and not to hoard.
public final class DD1SaveSnapshots {
    private DD1SaveSnapshots() {}

    public static final int KEPT = 3;

    private static File dir(File filesDir) {
        return new File(filesDir, "snapshots/saves");
    }

    public static File take(File filesDir, long atMillis) {
        File root = DD1Saves.root(filesDir);
        if (!DD1Saves.isSaveTree(root)) return null;

        File target = new File(dir(filesDir), Long.toString(atMillis));
        delete(target);
        if (!target.mkdirs()) return null;
        if (!copy(root, target)) {
            // A half-copied snapshot is worse than none: it would be restored one
            // day and look like a save.
            delete(target);
            return null;
        }
        prune(filesDir);
        return target;
    }

    public static List<File> kept(File filesDir) {
        List<File> snapshots = new ArrayList<>();
        File[] entries = dir(filesDir).listFiles();
        if (entries == null) return snapshots;
        for (File entry : entries) {
            if (entry.isDirectory() && takenAt(entry) > 0) snapshots.add(entry);
        }
        Collections.sort(snapshots, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(takenAt(right), takenAt(left));
            }
        });
        return snapshots;
    }

    private static void prune(File filesDir) {
        List<File> snapshots = kept(filesDir);
        for (int i = KEPT; i < snapshots.size(); i++) delete(snapshots.get(i));
    }

    private static long takenAt(File snapshot) {
        try {
            return Long.parseLong(snapshot.getName());
        }
        catch (NumberFormatException notASnapshot) {
            return -1;
        }
    }

    private static boolean copy(File from, File to) {
        File[] children = from.listFiles();
        if (children == null) return false;
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                if (!target.mkdirs() || !copy(child, target)) return false;
            }
            else if (!copyFile(child, target)) return false;
        }
        return true;
    }

    private static boolean copyFile(File from, File to) {
        byte[] buffer = new byte[8192];
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return true;
        }
        catch (IOException failed) {
            return false;
        }
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
Expected: PASS, no failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SaveSnapshots.java app/src/test/java/com/winlator/dd1/DD1SaveSnapshotsTest.java
git commit -m "feat: keep three snapshots of the saves"
```

---

### Task 4: Taking a snapshot before the game runs

**Files:**
- Modify: `app/src/main/java/com/winlator/dd1/DD1HomeFragment.java` - the Play button's listener
- Test: `app/src/test/java/com/winlator/dd1/DD1SavePolicyTest.java`
- Create: `app/src/main/java/com/winlator/dd1/DD1SavePolicy.java`

**Interfaces:**
- Consumes: `DD1SaveSnapshots.kept(File)`, `DD1SaveSummary.of(File)`, `DD1Saves.root(File)`.
- Produces: `static boolean worthTaking(File filesDir)` - true when there is a save tree and no snapshot yet matches it.

- [ ] **Step 1: Write the failing test**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DD1SavePolicyTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void thereIsNothingToTakeWithoutASave() throws IOException {
        assertFalse(DD1SavePolicy.worthTaking(folder.newFolder()));
    }

    @Test
    public void aSaveWithNoSnapshotIsWorthTaking() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");

        assertTrue(DD1SavePolicy.worthTaking(files));
    }

    // Launching the game twice without playing must not spend a snapshot slot,
    // or three launches would push out the state worth going back to.
    @Test
    public void aSaveAlreadySnapshottedIsNotWorthTakingAgain() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");
        DD1SaveSnapshots.take(files, 1000L);

        assertFalse(DD1SavePolicy.worthTaking(files));
    }

    @Test
    public void aSaveThatMovedOnIsWorthTakingAgain() throws IOException {
        File files = folder.newFolder();
        save(files, "abc");
        DD1SaveSnapshots.take(files, 1000L);
        save(files, "played some more");

        assertTrue(DD1SavePolicy.worthTaking(files));
    }

    private static void save(File files, String content) throws IOException {
        File dir = new File(DD1Saves.root(files), "profile_0");
        dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir, "persist.game.json"))) {
            out.write(content.getBytes("UTF-8"));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*DD1SavePolicyTest*'`
Expected: FAIL to compile with "cannot find symbol" for `DD1SavePolicy`.

- [ ] **Step 3: Write minimal implementation**

```java
package com.winlator.dd1;

import java.io.File;
import java.util.List;

// Whether a snapshot would say anything the ones already kept do not. Three
// slots are all there are, so a launch that changed nothing must not spend one.
public final class DD1SavePolicy {
    private DD1SavePolicy() {}

    public static boolean worthTaking(File filesDir) {
        File root = DD1Saves.root(filesDir);
        if (!DD1Saves.isSaveTree(root)) return false;
        List<DD1SaveSummary.Entry> now = DD1SaveSummary.of(root);
        for (File snapshot : DD1SaveSnapshots.kept(filesDir)) {
            if (DD1SaveSummary.changed(DD1SaveSummary.of(snapshot), now).isEmpty()) return false;
        }
        return true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests '*DD1SavePolicyTest*'`
Expected: PASS.

- [ ] **Step 5: Take the snapshot on the way into the game**

The Play button ends in `DD1HomeFragment.launch(Activity, Container, File)`, at
`app/src/main/java/com/winlator/dd1/DD1HomeFragment.java:408`. Add the snapshot
as the first thing that method does, before the drive is added and before the
intent is built:

```java
    private void launch(Activity activity, Container container, File executable) {
        // Before the game gets a chance to write, and only when the saves have
        // moved since the last one, because three slots are all there are.
        if (DD1SavePolicy.worthTaking(activity.getFilesDir()))
            DD1SaveSnapshots.take(activity.getFilesDir(), System.currentTimeMillis());

        File gameDir = new File(activity.getFilesDir(), "game");
```

Leave the rest of the method as it is. `java.io.File` is already imported there.

- [ ] **Step 6: Run the whole suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS, no failures.

- [ ] **Step 7: Check it on the phone**

The phone has a live campaign in `profile_0`. Press Play, leave the game, then:

```bash
adb -s <serial> shell "run-as com.winlator ls -la files/snapshots/saves"
adb -s <serial> shell "run-as com.winlator sh -c 'find files/snapshots/saves -name persist.game.json | head'"
```

Expected: one directory named with a millisecond timestamp, holding `profile_0/persist.game.json` with a non-zero length. Read the length, not the presence - a zero-byte copy is the failure this project keeps meeting.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SavePolicy.java app/src/test/java/com/winlator/dd1/DD1SavePolicyTest.java app/src/main/java/com/winlator/dd1/DD1HomeFragment.java
git commit -m "feat: snapshot the saves before the game runs"
```

---

## What this plan does not do

Steam Cloud transfer is the next plan. It consumes `DD1SaveSummary.Entry` and
`DD1SaveSummary.acceptable`, and the handler for it exists in javasteam 1.8.0:
`getAppFileListChange`, `clientFileDownload`, `beginAppUploadBatch`,
`beginFileUpload`, `commitFileUpload` and `completeAppUploadBatch`, confirmed
present on 2026-08-20. Conflict resolution stays a decision the player makes;
nothing about it is automatic.

A screen for the snapshots - listing them and restoring one - is also left out.
The snapshots are on disk and `adb` can read them, which is enough to be worth
having before the UI exists.

## Rules the cloud plan inherits

`iunius612/StS2-Launcher_Mod_Manager` (MIT) is an Android launcher for another
Steam game that reached this problem first and documents what went wrong before
it was fixed. Three of its conclusions are constraints on our cloud plan, and
they are not in our design doc:

- **An unknown cloud state is not an empty one.** Its sync used to let fresh
  defaults overwrite real progress; it now blocks until the cloud listing is
  actually known and falls back to local-only when it is not. Ours must do the
  same: no cloud listing means no upload and no delete, not "the cloud has
  nothing".
- **One funnel for cloud writes,** with the empty and abnormal ones refused
  there. Scattering that check is how one path ends up without it - which is
  exactly how this project shipped 3.7 GB of zeros once already.
- **Timestamps decide nothing.** Its upstream synced on mtime and lost saves.
  Ours compares SHA-1, which Task 2 already produces.

It also keeps one manual full-tree backup indefinitely, separate from the
FIFO-capped automatic ones. Worth having eventually: our three snapshots are
app-private, so a player without `adb` cannot get a campaign back out.

No code is taken from it. The credit is in `NOTICE`.
