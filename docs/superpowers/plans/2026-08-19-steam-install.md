# Steam Install Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When no valid DD1 payload exists, let the user sign into Steam, verify ownership, download the owned Windows game and DLC with visible progress/logs, validate it, and transition to Play.

**Architecture:** A bound Android foreground service owns the JavaSteam connection and DepotDownloader so activity recreation cannot interrupt work. Pure Java state, logging, ownership, and payload validation code sits behind the existing single-game home fragment; Android Keystore stores only the refresh token.

**Tech Stack:** Android Java, JavaSteam 1.8.0, javasteam-depotdownloader 1.8.0, ZXing Core, Android Keystore, JUnit 4

**Spec:** `docs/superpowers/specs/2026-08-19-dd1-android-launcher-design.md`

## Global Constraints

- Steam App ID is exactly `262060`.
- Download platform is exactly `windows`, branch `public`, architecture `64`.
- Passwords, cookies, authorization headers, and refresh tokens never enter logs.
- Game data is downloaded only after the signed-in account's package metadata establishes ownership.
- Incomplete downloads remain under `files/staging/game`; only a validated tree replaces `files/game`.
- The APK and repository contain no game or DLC payload.
- This plan excludes Steam Cloud implementation; it ends with a stable `files/saves` contract for the follow-up plan.

---

### Task 1: Prove JavaSteam Works in the Android Build

**Files:**
- Modify: `app/build.gradle`
- Test: `app/src/test/java/com/winlator/dd1/SteamDependencyTest.java`

**Interfaces:**
- Produces: Java 11 bytecode support and callable `AppItem`/`AuthSessionDetails` classes.

- [ ] **Step 1: Write the dependency smoke test before adding dependencies**

```java
package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import in.dragonbra.javasteam.depotdownloader.data.AppItem;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;
import java.util.Collections;
import org.junit.Test;

public class SteamDependencyTest {
    @Test public void createsWindowsDd1DownloadRequest() {
        AuthSessionDetails auth = new AuthSessionDetails();
        auth.persistentSession = true;
        AppItem item = new AppItem(262060, false, "/tmp/game", "public", "",
            false, "windows", false, "64", false, "english", false,
            Collections.emptyList(), Collections.emptyList(), true, false);
        assertEquals(262060, item.getAppId());
        assertEquals("windows", item.getOs());
    }
}
```

- [ ] **Step 2: Run it and confirm RED because JavaSteam classes are unavailable**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.SteamDependencyTest`

Expected: compilation fails with missing `in.dragonbra.javasteam` packages.

- [ ] **Step 3: Add only the required build settings and dependencies**

```groovy
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'in.dragonbra:javasteam:1.8.0'
    implementation 'in.dragonbra:javasteam-depotdownloader:1.8.0'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.83'
    implementation 'com.google.zxing:core:3.5.3'
}
```

Keep the existing XZ and Zstd declarations unless Gradle reports duplicate native libraries; Gradle's newest-version resolution should select the JavaSteam-compatible artifacts.

- [ ] **Step 4: Run the smoke test and the existing unit tests**

Run: `./gradlew testDebugUnitTest`

Expected: all tests pass and dependency resolution reports no duplicate classes.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle app/src/test/java/com/winlator/dd1/SteamDependencyTest.java
git commit -m "build: add JavaSteam depot dependencies"
```

### Task 2: Model Installer State, Logs, Ownership, and Payload Validation

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1InstallPhase.java`
- Create: `app/src/main/java/com/winlator/dd1/DD1InstallSnapshot.java`
- Create: `app/src/main/java/com/winlator/dd1/DD1InstallLog.java`
- Create: `app/src/main/java/com/winlator/dd1/DD1Ownership.java`
- Modify: `app/src/main/java/com/winlator/dd1/DD1Game.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1InstallerModelTest.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1OwnershipTest.java`
- Modify: `app/src/test/java/com/winlator/dd1/DD1GameTest.java`

**Interfaces:**
- Produces: `DD1InstallSnapshot`, `DD1InstallLog.append(String)`, `DD1Ownership.ownsApp(Map<Integer,List<Integer>>, int)`, and `DD1Game.validate(File)`.
- Consumes: app-private `filesDir` and package-to-app mappings returned by Steam PICS.

- [ ] **Step 1: Write failing behavior tests**

```java
@Test public void redactsSecretsAndBoundsVisibleLog() {
    DD1InstallLog log = new DD1InstallLog(2);
    log.append("Authorization: Bearer abc");
    log.append("file one");
    log.append("file two");
    assertEquals(Arrays.asList("file one", "file two"), log.visibleLines());
    assertFalse(log.fullText().contains("abc"));
}

@Test public void findsOwnershipInsideLicensedPackageApps() {
    Map<Integer, List<Integer>> packages = new HashMap<>();
    packages.put(123, Arrays.asList(10, 262060, 20));
    assertTrue(DD1Ownership.ownsApp(packages, 262060));
    assertFalse(DD1Ownership.ownsApp(packages, 99));
}

@Test public void rejectsPayloadMissingRequiredDirectory() throws Exception {
    File game = Files.createTempDirectory("dd1-invalid").toFile();
    File exe = new File(game, "_windows/win64/Darkest.exe");
    exe.getParentFile().mkdirs();
    exe.createNewFile();
    assertEquals("audio", DD1Game.validate(game).missingPath);
}
```

- [ ] **Step 2: Run tests and confirm RED on missing types and methods**

Run: `./gradlew testDebugUnitTest --tests 'com.winlator.dd1.DD1*Test'`

Expected: compilation fails for `DD1InstallLog`, `DD1Ownership`, and `DD1Game.validate`.

- [ ] **Step 3: Implement the immutable snapshot and minimal rules**

```java
public enum DD1InstallPhase {
    SIGNED_OUT, AUTHENTICATING, NOT_OWNED, READY_TO_INSTALL,
    DOWNLOADING, VERIFYING, READY, ERROR
}

public final class DD1InstallSnapshot {
    public final DD1InstallPhase phase;
    public final long downloadedBytes, totalBytes, bytesPerSecond;
    public final String message, currentFile, challengeUrl;
    public final List<String> logLines;

    public DD1InstallSnapshot(DD1InstallPhase phase, long downloadedBytes,
            long totalBytes, long bytesPerSecond, String message,
            String currentFile, String challengeUrl, List<String> logLines) {
        this.phase = phase;
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.bytesPerSecond = bytesPerSecond;
        this.message = message;
        this.currentFile = currentFile;
        this.challengeUrl = challengeUrl;
        this.logLines = Collections.unmodifiableList(new ArrayList<>(logLines));
    }
}
```

`DD1InstallLog` must replace case-insensitive lines containing `authorization`, `password`, `refresh_token`, `access_token`, or `cookie` with `[REDACTED]`, append that sanitized value to the file text, and retain only the last configured number of visible lines.

`DD1Ownership.ownsApp` returns true only when one of the licensed package app lists contains `262060`.

`DD1Game.validate(gameDir)` checks, in order, the executable and `audio`, `campaign`, `dungeons`, `heroes`, and `shared`; its `Validation` contains `valid` and the first `missingPath`. `findExecutable(filesDir)` delegates to validation of `files/game`.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `./gradlew testDebugUnitTest --tests 'com.winlator.dd1.DD1*Test'`

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1 app/src/test/java/com/winlator/dd1
git commit -m "feat: model DD1 Steam installation state"
```

### Task 3: Store the Steam Refresh Token Safely

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/SteamTokenStore.java`
- Create: `app/src/androidTest/java/com/winlator/dd1/SteamTokenStoreTest.java`
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: `SteamTokenStore.save(String account, String token)`, `load()`, and `clear()`.
- Consumes: Android Keystore alias `dd1-steam-refresh` and private SharedPreferences `steam_session`.

- [ ] **Step 1: Add instrumentation runner/dependency and write the failing round-trip test**

```groovy
defaultConfig {
    testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
}
dependencies {
    androidTestImplementation 'androidx.test:core:1.5.0'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
}
```

```java
@RunWith(AndroidJUnit4.class)
public class SteamTokenStoreTest {
    @Test public void encryptsLoadsAndClearsRefreshToken() {
        Context context = ApplicationProvider.getApplicationContext();
        SteamTokenStore store = new SteamTokenStore(context);
        store.clear();
        store.save("owner", "secret-token");
        assertEquals("owner", store.load().account);
        assertEquals("secret-token", store.load().token);
        assertFalse(context.getSharedPreferences("steam_session", 0)
            .getString("ciphertext", "").contains("secret-token"));
        store.clear();
        assertNull(store.load());
    }
}
```

- [ ] **Step 2: Run on Waydroid and confirm RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.winlator.dd1.SteamTokenStoreTest`

Expected: compilation fails because `SteamTokenStore` does not exist.

- [ ] **Step 3: Implement AES/GCM Keystore storage**

Use `KeyGenParameterSpec` with AES/GCM, no padding, encrypt a UTF-8 token with a fresh 12-byte IV, and store Base64 IV/ciphertext plus account name in `steam_session`. `load()` returns null for absent or undecryptable data and clears corrupt data. `clear()` removes preferences and deletes alias `dd1-steam-refresh`.

- [ ] **Step 4: Run the instrumentation test and unit suite**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.winlator.dd1.SteamTokenStoreTest && ./gradlew testDebugUnitTest`

Expected: both commands pass.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle app/src/main/java/com/winlator/dd1/SteamTokenStore.java app/src/androidTest/java/com/winlator/dd1/SteamTokenStoreTest.java
git commit -m "feat: protect Steam refresh token"
```

### Task 4: Implement QR Authentication and Ownership Resolution

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1SteamSession.java`
- Create: `app/src/main/java/com/winlator/dd1/DD1SteamEvents.java`
- Test: `app/src/test/java/com/winlator/dd1/DD1SteamEventsTest.java`

**Interfaces:**
- Produces: `DD1SteamSession.startQr()`, `restore()`, `signOut()`, `licenses()`, and listener callbacks carrying snapshots.
- Consumes: `SteamTokenStore`, JavaSteam `SteamClient`, `CallbackManager`, `SteamUser`, `SteamApps`, license and PICS callbacks.

- [ ] **Step 1: Test event-to-state behavior without mocking JavaSteam**

```java
@Test public void ownedPackagesEnableInstallOnlyAfterLoginAndPics() {
    DD1SteamEvents events = new DD1SteamEvents();
    assertEquals(DD1InstallPhase.AUTHENTICATING, events.authStarted("url").phase);
    events.loggedOn();
    assertEquals(DD1InstallPhase.READY_TO_INSTALL,
        events.packagesResolved(Collections.singletonMap(1, Arrays.asList(262060))).phase);
}

@Test public void unownedPackagesNeverEnableDownload() {
    DD1SteamEvents events = new DD1SteamEvents();
    events.loggedOn();
    assertEquals(DD1InstallPhase.NOT_OWNED,
        events.packagesResolved(Collections.singletonMap(1, Arrays.asList(10))).phase);
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1SteamEventsTest`

Expected: compilation fails because the event reducer does not exist.

- [ ] **Step 3: Implement the reducer, then the real Steam boundary**

`DD1SteamSession` owns one executor and one callback loop. `startQr()` connects, calls `beginAuthSessionViaQR`, publishes the challenge URL, waits for `pollingWaitForResult`, stores the refresh token, and logs on with `LogOnDetails`. `restore()` uses the stored account/token. On `LicenseListCallback`, request PICS package info for each distinct licensed package ID with its access token; parse each package's `appids` children and pass the complete mapping to `DD1Ownership`.

The session publishes `READY_TO_INSTALL` only when App ID `262060` appears. Every subscription is closed and the callback loop is interrupted by `signOut()`. Exceptions publish sanitized `ERROR` snapshots and never include authentication values.

- [ ] **Step 4: Run the reducer tests and assemble the APK**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1SteamEventsTest assembleDebug`

Expected: tests and APK assembly pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1SteamSession.java app/src/main/java/com/winlator/dd1/DD1SteamEvents.java app/src/test/java/com/winlator/dd1/DD1SteamEventsTest.java
git commit -m "feat: authenticate Steam owner with QR"
```

### Task 5: Download and Atomically Install in a Foreground Service

**Files:**
- Create: `app/src/main/java/com/winlator/dd1/DD1InstallService.java`
- Create: `app/src/main/java/com/winlator/dd1/DD1Installer.java`
- Create: `app/src/test/java/com/winlator/dd1/DD1InstallerTest.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: bound `DD1InstallService.LocalBinder`, `observe(Listener)`, `startQr()`, `download()`, `cancel()`, `signOut()`.
- Consumes: authenticated `DD1SteamSession`, license list, `DepotDownloader`, `DD1Game.validate`, and app-private directories.

- [ ] **Step 1: Test atomic replacement with real temporary directories**

```java
@Test public void invalidStagingNeverReplacesInstalledGame() throws Exception {
    File files = Files.createTempDirectory("dd1-install").toFile();
    File active = new File(files, "game");
    active.mkdirs();
    Files.write(new File(active, "marker").toPath(), new byte[]{1});
    File staging = new File(files, "staging/game");
    staging.mkdirs();
    DD1Installer.Result result = DD1Installer.activate(files);
    assertFalse(result.success);
    assertTrue(new File(active, "marker").isFile());
}

@Test public void validStagingReplacesInstalledGame() throws Exception {
    File files = Files.createTempDirectory("dd1-valid-install").toFile();
    File staging = new File(files, "staging/game");
    File exe = new File(staging, "_windows/win64/Darkest.exe");
    exe.getParentFile().mkdirs();
    exe.createNewFile();
    for (String path : Arrays.asList("audio", "campaign", "dungeons", "heroes", "shared"))
        new File(staging, path).mkdirs();
    assertTrue(DD1Installer.activate(files).success);
    assertTrue(new File(files, "game/_windows/win64/Darkest.exe").isFile());
    assertFalse(new File(files, "staging/game").exists());
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1InstallerTest`

Expected: compilation fails because `DD1Installer` does not exist.

- [ ] **Step 3: Implement activation and service**

`DD1Installer.activate(filesDir)` validates staging, renames existing `game` to `staging/previous-game`, renames staging to `game`, restores the previous directory if the second rename fails, and deletes `previous-game` only after success.

Declare `android.permission.FOREGROUND_SERVICE` and a non-exported `DD1InstallService`. The service creates notification channel `dd1_install`, starts foreground work before network access, and constructs:

```java
AppItem item = new AppItem(262060, false, staging.getAbsolutePath(),
    "public", "", false, "windows", false, "64", false, "english",
    false, Collections.emptyList(), Collections.emptyList(), true, false);
DepotDownloader downloader = new DepotDownloader(client, licenses, true,
    false, 8, 4, 1, true);
downloader.addListener(progressListener);
downloader.add(item);
downloader.finishAdding();
downloader.awaitCompletion();
```

The listener maps status, file, chunk, depot, completion, and failure callbacks into immutable snapshots and appends sanitized lines to the session log. Completion runs `DD1Installer.activate`; only successful activation publishes `READY`.

- [ ] **Step 4: Run tests and assemble**

Run: `./gradlew testDebugUnitTest --tests com.winlator.dd1.DD1InstallerTest assembleDebug`

Expected: tests pass and the foreground service is present in the merged manifest.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/dd1/DD1InstallService.java app/src/main/java/com/winlator/dd1/DD1Installer.java app/src/test/java/com/winlator/dd1/DD1InstallerTest.java app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "feat: download and install owned DD1 depots"
```

### Task 6: Replace the Missing-Game Screen With Steam Install UI

**Files:**
- Modify: `app/src/main/java/com/winlator/DD1HomeFragment.java`
- Modify: `app/src/main/res/layout/dd1_home_fragment.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/androidTest/java/com/winlator/DD1HomeFragmentTest.java`

**Interfaces:**
- Consumes: `DD1InstallService.LocalBinder` snapshots and actions.
- Produces: QR sign-in, owned-content Download, progress/log, retry, and ready Play states.

- [ ] **Step 1: Write an Android test for the missing-game state**

The test launches `MainActivity` with an empty app game directory and verifies that `BTSteamLogin` is visible while `BTPrimaryAction` is absent. Inject a service snapshot through a package-visible `renderInstallSnapshot` method and verify that `READY_TO_INSTALL` displays `BTDownload`, while `DOWNLOADING` displays `PBDownload`, `TVDownloadFile`, and `TVInstallLog`.

- [ ] **Step 2: Run on Waydroid and confirm RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.winlator.DD1HomeFragmentTest`

Expected: test fails because the Steam install views do not exist.

- [ ] **Step 3: Add the minimal state-driven views and bind the service**

Keep the existing title/status/Play controls. Add one hidden install group containing QR `ImageView`, Login, credential fallback, Download, progress bar, current-file text, monospace scrollable log, Cancel, Retry, and Sign out. `renderInstallSnapshot` makes exactly the controls for the current phase visible. The fragment binds in `onStart`, unregisters and unbinds in `onStop`, and never owns the running download thread.

Generate the QR bitmap locally with ZXing from `snapshot.challengeUrl`; never request an external QR rendering service. When `DD1Game.validate(files/game)` becomes valid, render Play and hide installation controls.

- [ ] **Step 4: Run all checks**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug`

Expected: all checks pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/winlator/DD1HomeFragment.java app/src/main/res/layout/dd1_home_fragment.xml app/src/main/res/values/strings.xml app/src/androidTest/java/com/winlator/DD1HomeFragmentTest.java
git commit -m "feat: show Steam installation flow on DD1 home"
```

### Task 7: Visible Waydroid Acceptance Check

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: debug APK and the existing Gamescope-hosted Waydroid infrastructure.
- Produces: documented, reproducible authentication and installer verification.

- [ ] **Step 1: Build and install the APK**

Run: `./gradlew assembleDebug`

Run: `waydroid app install app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: Exercise the visible flow in Gamescope**

Verify QR rendering, Steam Mobile approval, ownership result, owned DLC resolution, Download, byte/file progress, scrolling logs, rotation/rebind, network interruption, resume, validation, and transition to Play. Do not enter or expose a password in captured logs.

- [ ] **Step 3: Inspect app logs for secrets and installer failures**

Run: `adb logcat -d | rg -i 'dd1|javasteam|depot|exception|authorization|refresh|password'`

Expected: progress and errors are visible; no credential or token value appears.

- [ ] **Step 4: Document the user flow and known Waydroid runtime limit**

Update README to state that installation is now performed in-app and that Waydroid validates installer behavior but its x86_64 Houdini path does not prove ARM64 gameplay.

- [ ] **Step 5: Run final verification and commit**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug`

```bash
git add README.md
git commit -m "docs: document Steam installation flow"
```
