# DD1 Mod Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install, update, list, and explicitly delete subscribed Workshop and local DD1 mods from the launcher.

**Architecture:** Reuse the existing Steam session, foreground install service, `ModSyncPlan`, and `PubFileItem`. Keep disk safety in one pure-Java `DD1Workshop` boundary; expose immutable Workshop snapshots from the service; render them in one drawer fragment.

**Tech Stack:** Java 8, AndroidX fragments, JUnit 4, Android instrumentation, JavaSteam 1.8.0, JavaSteam DepotDownloader 1.8.0.

**Spec:** `docs/superpowers/specs/2026-08-21-dd1-mod-manager-design.md`

## Global Constraints

- No game, DLC, Workshop payload, art, or FMOD binary enters the APK or repository.
- `applicationId` stays `com.winlator`; `targetSdkVersion` stays `28`.
- Local launcher edits stay below `com.winlator.dd1` and `dd1_*` resources.
- Workshop updates are explicit and never replace the last good copy on failure.
- The launcher does not edit campaign enable state or load order.
- `./gradlew assembleDebug testDebugUnitTest` must pass after every task.
- `connectedDebugAndroidTest` runs only against Waydroid, never the phone.

---

### Task 1: Safe Local Workshop Storage

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1Workshop.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1WorkshopTest.java`

**Interfaces:**
- Produces: `DD1Workshop.Mod`, `scan(File)`, `staging(File,long)`, `promote(File,long,long,String)`, and `delete(File,String)`.
- Consumes: direct app-private `filesDir`; no Android APIs.

- [ ] **Step 1: Write failing marker and scan tests**

```java
@Test public void markerMakesADirectoryAWorkshopInstall() throws Exception {
    File mod = mkdir("game/mods/42");
    Files.write(new File(mod, ".dd1-workshop").toPath(),
        "42\n1700000000\nMusketeer\n".getBytes(StandardCharsets.UTF_8));
    DD1Workshop.Mod found = DD1Workshop.scan(root).get(0);
    assertEquals(42L, found.publishedFileId);
    assertEquals("Musketeer", found.title);
}

@Test public void anUnmarkedDirectoryRemainsLocal() throws Exception {
    mkdir("game/mods/my-local-mod");
    DD1Workshop.Mod found = DD1Workshop.scan(root).get(0);
    assertEquals(0L, found.publishedFileId);
    assertEquals("my-local-mod", found.title);
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1WorkshopTest`

Expected: compilation fails because `DD1Workshop` does not exist.

- [ ] **Step 3: Implement marker parsing and direct-child scanning**

Use `java.nio.file.Files`, `StandardCharsets.UTF_8`, and `File.listFiles(File::isDirectory)`. Invalid markers become local mods. Sort by directory name for stable UI and tests.

- [ ] **Step 4: Add failing promotion, rollback, shape, and delete-confinement tests**

```java
@Test public void promotionKeepsRecognizablePayloadAndWritesMarker() throws Exception {
    File stage = mkdir("workshop-staging/42/only-child");
    touch(new File(stage, "project.xml"));
    DD1Workshop.promote(root, 42, 7, "Musketeer");
    assertTrue(new File(root, "game/mods/42/project.xml").isFile());
}

@Test public void payloadWithoutProjectXmlCannotReplaceInstalledCopy() throws Exception {
    File old = mkdir("game/mods/42");
    touch(new File(old, "old"));
    mkdir("workshop-staging/42");
    assertThrows(IOException.class, () -> DD1Workshop.promote(root, 42, 8, "Bad"));
    assertTrue(new File(old, "old").isFile());
}

@Test public void deleteRefusesAnythingButADirectChild() throws Exception {
    assertThrows(IOException.class, () -> DD1Workshop.delete(root, "../game"));
}
```

- [ ] **Step 5: Run RED, implement minimal staged rename with rollback, then run GREEN**

Promotion resolves either the staging root or its sole child containing `project.xml`, rejects symbolic links, renames an existing target to `<id>.dd1-backup`, promotes the payload, and restores the backup on failure. Recursive deletion uses the existing `FileUtils.delete` only after canonical direct-child validation.

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1WorkshopTest`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1Workshop.java app/src/test/java/com/winlator/dd1/DD1WorkshopTest.java
git commit -m "feat: manage workshop payloads safely"
```

### Task 2: Steam Workshop Catalog

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1WorkshopCatalog.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1WorkshopCatalogTest.java`
- Modify: `app/src/main/java/com/winlator/dd1/DD1SteamSession.java`

**Interfaces:**
- Produces: `DD1WorkshopCatalog.fromDetails(List<PublishedFileDetails>)` and `DD1SteamSession.workshop()` returning a `CompletableFuture<List<ModSyncPlan.Subscribed>>`.
- Consumes: `PublishedFile.GetUserFiles`, app id `262060`, page size `100`.

- [ ] **Step 1: Write failing protobuf conversion tests**

```java
@Test public void usableDetailBecomesDownloadableSubscription() {
    PublishedFileDetails detail = PublishedFileDetails.newBuilder()
        .setPublishedfileid(42).setTitle("Musketeer").setTimeUpdated(7)
        .setConsumerAppid(262060).setHcontentFile(99).build();
    ModSyncPlan.Subscribed item = DD1WorkshopCatalog.fromDetails(Arrays.asList(detail)).get(0);
    assertEquals(42L, item.publishedFileId);
    assertTrue(item.downloadable);
}
```

Add separate cases for wrong consumer app, zero content handle, and non-success detail result; each remains visible but not downloadable.

- [ ] **Step 2: Run RED, implement the converter, and run GREEN**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1WorkshopCatalogTest`

- [ ] **Step 3: Add the existing Steam client's `PublishedFile` service and paged request**

Create it from `SteamUnifiedMessages`, request `GetUserFiles` with app `262060`, type `subscribed`, page starting at `1`, and `numperpage=100`; stop when the accumulated count reaches `total` or a page is empty. Execute through the session's existing `operations` executor and fail the returned future on non-`OK` responses.

- [ ] **Step 4: Compile and run all unit tests**

Run: `./gradlew assembleDebug testDebugUnitTest`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1WorkshopCatalog.java app/src/main/java/com/winlator/dd1/DD1SteamSession.java app/src/test/java/com/winlator/dd1/DD1WorkshopCatalogTest.java
git commit -m "feat: list subscribed workshop items"
```

### Task 3: Workshop Transfer State in the Existing Service

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1WorkshopSnapshot.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1WorkshopSnapshotTest.java`
- Modify: `app/src/main/java/com/winlator/dd1/DD1InstallService.java`

**Interfaces:**
- Produces: `observeWorkshop`, `removeWorkshopObserver`, `refreshWorkshop`, `syncWorkshop`, `deleteMod`, and `workshopSnapshot`.
- Consumes: `DD1SteamSession.workshop()`, `DD1Workshop.scan`, `ModSyncPlan.of`, `PubFileItem`.

- [ ] **Step 1: Write failing snapshot-plan tests**

Construct subscribed and scanned lists and assert that the snapshot exposes install/update/orphan/local rows without mutating either input. Assert `syncable()` is true only for install/update buckets.

- [ ] **Step 2: Run RED, implement immutable snapshot creation, and run GREEN**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1WorkshopSnapshotTest`

- [ ] **Step 3: Wire explicit refresh and observer delivery**

Refresh obtains Steam subscriptions asynchronously, scans disk on the worker, publishes loading/list/error snapshots on the main handler, and never changes the normal install snapshot used by the home screen.

- [ ] **Step 4: Wire serial `PubFileItem` downloads**

For every install/update item, reset its staging directory, create a downloader with the same Android-safe limits as game downloads, submit `new PubFileItem(262060, id, false, stagingPath, false, false)`, wait with the existing stall guard, then call `DD1Workshop.promote`. Publish row-level progress and continue after an item failure. Prevent overlap through the existing `downloader != null` guard.

- [ ] **Step 5: Add confined explicit deletion and refresh**

`deleteMod(String directoryName)` calls `DD1Workshop.delete` on the worker and republishes the reconciled snapshot. It never accepts an absolute path or Workshop title.

- [ ] **Step 6: Run full host verification and commit**

Run: `./gradlew assembleDebug testDebugUnitTest`

```bash
git add app/src/main/java/com/winlator/dd1/DD1WorkshopSnapshot.java app/src/main/java/com/winlator/dd1/DD1InstallService.java app/src/test/java/com/winlator/dd1/DD1WorkshopSnapshotTest.java
git commit -m "feat: sync workshop mods through the install service"
```

### Task 4: Workshop Screen

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1WorkshopFragment.java`
- Create: `app/src/main/res/layout/dd1_workshop_fragment.xml`
- Create: `app/src/main/res/layout/dd1_workshop_row.xml`
- Modify: `app/src/main/java/com/winlator/dd1/DD1Activity.java`
- Modify: `app/src/main/res/layout/dd1_activity.xml`
- Modify: `app/src/main/res/values/dd1_strings.xml`
- Modify: `app/src/main/res/values-ko/dd1_strings.xml`

**Interfaces:**
- Consumes: workshop observer and actions from `DD1InstallService`.
- Produces: drawer navigation and list UI.

- [ ] **Step 1: Add the drawer button and screen layouts**

Reuse `dd1_screen_header`. The screen contains loading/error/empty labels, `LLWorkshopList`, sync and refresh buttons, a horizontal progress bar, and the same bounded monospace log style as saves.

- [ ] **Step 2: Implement fragment binding and rendering**

Bind in `onStart`, observe, refresh once, unobserve/unbind in `onStop`. Inflate one row per snapshot item, showing title, source/state, and id. Installed rows show Delete; the bottom Sync button appears only when `syncable()` and no transfer is active.

- [ ] **Step 3: Add delete confirmation and drawer listener**

The dialog names the mod and passes only its directory name to `deleteMod`.

- [ ] **Step 4: Compile, inspect resource errors, and commit**

Run: `./gradlew assembleDebug testDebugUnitTest`

```bash
git add app/src/main/java/com/winlator/dd1/DD1WorkshopFragment.java app/src/main/java/com/winlator/dd1/DD1Activity.java app/src/main/res/layout/dd1_activity.xml app/src/main/res/layout/dd1_workshop_fragment.xml app/src/main/res/layout/dd1_workshop_row.xml app/src/main/res/values/dd1_strings.xml app/src/main/res/values-ko/dd1_strings.xml
git commit -m "feat: add the workshop manager screen"
```

### Task 5: Waydroid Instrumentation and Visual Check

**Files:**
- Create: `app/src/androidTest/java/com/winlator/dd1/DD1WorkshopFragmentTest.java`

**Interfaces:**
- Consumes: real activity, fragment, layouts, and app-private files.
- Produces: repeatable Waydroid UI and promotion checks.

- [ ] **Step 1: Write the failing drawer-navigation test**

Launch `DD1Activity`, open the drawer, press Workshop, and assert the shared header title and refresh affordance are visible.

- [ ] **Step 2: Run RED against Waydroid**

Connect with `adb connect 192.168.240.112:5555`, verify that serial is Waydroid, then run:

`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.winlator.dd1.DD1WorkshopFragmentTest`

- [ ] **Step 3: Add fixture-driven local-row and delete-confirmation tests**

Create an unmarked `files/game/mods/local-fixture/project.xml`, navigate to the screen, assert the local source label, press Delete, confirm, and assert the direct child is gone.

- [ ] **Step 4: Run GREEN and full verification**

Run:

```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

- [ ] **Step 5: Install and visually inspect on Waydroid**

Install the debug APK, launch `com.winlator`, navigate to Workshop, capture a screenshot, and verify no clipping at the Waydroid landscape size.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/java/com/winlator/dd1/DD1WorkshopFragmentTest.java
git commit -m "test: exercise the workshop screen on waydroid"
```

