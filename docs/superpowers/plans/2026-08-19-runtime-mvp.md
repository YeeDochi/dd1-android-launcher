# DD1 Android Runtime MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run a user-owned Darkest Dungeon Linux payload in a visible Gamescope-hosted Waydroid session and on ARM64 Android, with touch, audio, saves, and exportable diagnostics.

**Architecture:** A standard Android app owns an embedded X server and launches the official Linux x86_64 no-Steam executable in a fresh native process. The Waydroid x86_64 flavor uses a bundled glibc loader; the ARM64 flavor uses Box64. Zink/Vulkan is primary, MobileGlues/OpenGL ES is fallback, and ALSA is bridged to AAudio.

**Tech Stack:** Java 17, Android Gradle Plugin 8.13.2, Android SDK 35, NDK 27.0.12077973, CMake, JUnit 4, Box64, embedded Winlator-lineage X server, Mesa/Zink, MobileGlues, AAudio.

**Spec:** `docs/superpowers/specs/2026-08-19-dd1-android-launcher-design.md`

## Global Constraints

- License the application GPL-3.0-or-later and preserve all third-party notices.
- Set `minSdk 30`, `compileSdk 35`, `targetSdk 35`, NDK `27.0.12077973`, and Java 17.
- Build `arm64-v8a` for phones and `x86_64` for Waydroid.
- Link every packaged native executable and library for 16 KiB page compatibility.
- Never package or commit Darkest Dungeon executables, assets, DLC, credentials, or saves.
- Keep mutable payload, saves, mods, cache, and logs in separate app-private directories.
- Use `_linuxnosteam/darkest.bin.x86_64`; do not add Wine or Termux.
- Show every Waydroid manual test in a Gamescope window.

---

### Task 1: Android project and visible Waydroid development loop

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/io/github/dd1android/launcher/LauncherActivity.java`
- Create: `app/src/main/res/layout/activity_launcher.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `scripts/run-waydroid-gamescope.sh`
- Create: `scripts/install-waydroid-debug.sh`
- Create: `LICENSE`
- Create: `NOTICE`
- Create: `.gitignore`
- Test: `app/src/test/java/io/github/dd1android/launcher/ProjectConfigTest.java`

**Interfaces:**
- Produces: package `io.github.dd1android.launcher`, launcher activity, repeatable build/install commands, and a visible Waydroid session.

- [ ] **Step 1: Write the failing configuration test**

```java
package io.github.dd1android.launcher;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class ProjectConfigTest {
    @Test public void appIdentityIsStable() {
        assertEquals("io.github.dd1android.launcher", BuildConfig.APPLICATION_ID);
    }
}
```

- [ ] **Step 2: Run the test and confirm the project is absent**

Run: `./gradlew testDebugUnitTest`

Expected: FAIL because the Gradle wrapper/project does not exist.

- [ ] **Step 3: Create the minimal Android project**

Use application ID and namespace `io.github.dd1android.launcher`, `minSdk = 30`, `compileSdk = 35`, `targetSdk = 35`, Java 17, view binding, JUnit 4.13.2, and ABI filters `arm64-v8a` and `x86_64`. The first activity displays three labels: payload state, runtime state, and last launch result.

The Gamescope launcher must use the existing desktop session instead of a headless compositor:

```sh
#!/bin/sh
set -eu
gamescope -W 1280 -H 720 -w 1280 -h 720 -f -- \
  waydroid show-full-ui
```

The install script must refuse to proceed unless `waydroid status` reports a running session and `adb devices` contains a device.

- [ ] **Step 4: Build, test, and display the app**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: BUILD SUCCESSFUL.

Run in a visible desktop terminal: `scripts/run-waydroid-gamescope.sh`

Expected: a 1280x720 full-screen Gamescope window shows Waydroid.

Run: `scripts/install-waydroid-debug.sh`

Expected: the DD1 Android Launcher activity opens inside the visible Gamescope window.

- [ ] **Step 5: Commit**

```bash
git add .gitignore LICENSE NOTICE settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew app scripts
git commit -m "chore: bootstrap Android launcher and visible Waydroid loop"
```

### Task 2: Payload directories, validation, and developer import

**Files:**
- Create: `app/src/main/java/io/github/dd1android/launcher/storage/AppPaths.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/payload/ElfHeader.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/payload/PayloadValidator.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/payload/ValidationResult.java`
- Create: `scripts/import-local-game.sh`
- Modify: `app/src/main/java/io/github/dd1android/launcher/LauncherActivity.java`
- Test: `app/src/test/java/io/github/dd1android/launcher/payload/PayloadValidatorTest.java`

**Interfaces:**
- Produces: `AppPaths.create(File filesDir)`, `ElfHeader.read(Path)`, and `PayloadValidator.validate(Path): ValidationResult`.
- `ValidationResult` is `record ValidationResult(boolean valid, List<String> errors)`.

- [ ] **Step 1: Write failing payload tests**

```java
@Test public void acceptsDd1LinuxPayload() throws Exception {
    Path root = temp.newFolder("game").toPath();
    TestPayload.writeElf64X86(root.resolve("_linuxnosteam/darkest.bin.x86_64"));
    Files.createDirectories(root.resolve("_linuxnosteam/lib64"));
    Files.write(root.resolve("_linuxnosteam/lib64/libSDL2-2.0.so.0"), new byte[] {1});
    Files.write(root.resolve("_linuxnosteam/lib64/libfmod.so.14"), new byte[] {1});
    Files.write(root.resolve("_linuxnosteam/lib64/libfmodstudio.so.14"), new byte[] {1});
    Files.createDirectories(root.resolve("campaign"));
    assertTrue(PayloadValidator.validate(root).valid());
}

@Test public void rejectsWrongElfMachine() throws Exception {
    Path root = temp.newFolder("bad").toPath();
    TestPayload.writeElf64Arm(root.resolve("_linuxnosteam/darkest.bin.x86_64"));
    ValidationResult result = PayloadValidator.validate(root);
    assertFalse(result.valid());
    assertTrue(result.errors().contains("darkest.bin.x86_64 is not an x86_64 ELF"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*PayloadValidatorTest'`

Expected: FAIL because payload classes do not exist.

- [ ] **Step 3: Implement the minimum validator**

Validate ELF magic, ELFCLASS64, little endian, and machine `EM_X86_64 = 62`; require the executable, bundled SDL2/FMOD libraries, and `campaign/`. Return all validation failures without throwing for user-correctable payload problems.

`AppPaths` creates exactly `game`, `runtime`, `saves`, `mods`, `cache`, and `logs` under `filesDir`.

The import script accepts one explicit source argument, verifies it with host `file`, pushes into the app-specific external import directory, and launches the app's debug import intent:

```sh
source_dir=$1
test -f "$source_dir/_linuxnosteam/darkest.bin.x86_64"
adb shell mkdir -p /sdcard/Android/data/io.github.dd1android.launcher/files/import
adb push "$source_dir/." /sdcard/Android/data/io.github.dd1android.launcher/files/import/
adb shell am start -a io.github.dd1android.launcher.IMPORT_DEBUG \
  -n io.github.dd1android.launcher/.LauncherActivity
```

- [ ] **Step 4: Run unit tests and validate the installed source payload on host**

Run: `./gradlew testDebugUnitTest`

Expected: PASS.

Run: `file /home/ljh/.local/share/Steam/steamapps/common/DarkestDungeon/_linuxnosteam/darkest.bin.x86_64`

Expected: `ELF 64-bit LSB executable, x86-64`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main scripts/import-local-game.sh
git commit -m "feat: validate and import owned DD1 payloads"
```

### Task 3: Launch configuration and renderer fallback policy

**Files:**
- Create: `app/src/main/java/io/github/dd1android/launcher/runtime/Renderer.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/runtime/DeviceCaps.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/runtime/LaunchConfig.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/runtime/LaunchConfigFactory.java`
- Test: `app/src/test/java/io/github/dd1android/launcher/runtime/LaunchConfigFactoryTest.java`

**Interfaces:**
- Produces: `enum Renderer { ZINK, MOBILE_GLUES, WAYDROID_MESA }`.
- Produces: `LaunchConfigFactory.create(DeviceCaps, AppPaths, Renderer): LaunchConfig`.
- `LaunchConfig` contains executable, working directory, environment map, 1280x720 surface size, and one fallback renderer.

- [ ] **Step 1: Write failing policy tests**

```java
@Test public void adrenoUsesZinkThenMobileGlues() {
    LaunchConfig c = factory.create(new DeviceCaps("arm64-v8a", "Adreno (TM) 650", false), paths, null);
    assertEquals(Renderer.ZINK, c.renderer());
    assertEquals(Renderer.MOBILE_GLUES, c.fallbackRenderer());
}

@Test public void waydroidUsesHostMesaWithoutFallbackLoop() {
    LaunchConfig c = factory.create(new DeviceCaps("x86_64", "AMD Radeon 860M", true), paths, null);
    assertEquals(Renderer.WAYDROID_MESA, c.renderer());
    assertNull(c.fallbackRenderer());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*LaunchConfigFactoryTest'`

Expected: FAIL because runtime policy types do not exist.

- [ ] **Step 3: Implement deterministic configuration**

Set `HOME`, `XDG_CONFIG_HOME`, `XDG_DATA_HOME`, `XDG_CACHE_HOME`, `BOX64_LOG_FILE`, `BOX64_DYNACACHE=1`, `BOX64_LD_LIBRARY_PATH`, `DISPLAY=:0`, and renderer-specific variables from absolute app paths. Do not inherit arbitrary application environment variables.

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/dd1android/launcher/runtime app/src/test/java/io/github/dd1android/launcher/runtime
git commit -m "feat: define deterministic DD1 launch policy"
```

### Task 4: Embedded X server and Android game surface

**Files:**
- Create: `app/src/main/java/io/github/dd1android/launcher/game/GameActivity.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/game/GameSurface.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/xserver/**`
- Create: `app/src/main/java/io/github/dd1android/launcher/xconnector/**`
- Create: `app/src/main/cpp/xserver/**`
- Create: `app/src/main/cpp/dd1_xserver_jni.c`
- Create: `app/src/main/cpp/CMakeLists.txt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/androidTest/java/io/github/dd1android/launcher/game/GameSurfaceTest.java`

**Interfaces:**
- Produces: `GameActivity.start(Context, LaunchConfig)`.
- Produces: native Unix socket X server at display `:0` and a `Surface` lifecycle callback.

- [ ] **Step 1: Write a failing instrumentation test**

```java
@Test public void surfaceRecreationRestartsPresentationWithoutChangingDisplay() {
    ActivityScenario<GameActivity> scenario = ActivityScenario.launch(GameActivity.class);
    scenario.recreate();
    scenario.onActivity(a -> assertEquals(":0", a.getXDisplayName()));
}
```

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.dd1android.launcher.game.GameSurfaceTest`

Expected: FAIL because `GameActivity` is absent.

- [ ] **Step 3: Port only the X server boundary from RimDroid**

Copy the GPL X server and connector packages from the pinned RimDroid source revision recorded in `NOTICE`; rename packages and JNI symbols from `com.rimdroid` to `io.github.dd1android.launcher`. Exclude RimWorld setup, save patches, FMOD decoding, profiles, and launcher fragments. Keep one full-screen root window and one game client.

- [ ] **Step 4: Build and exercise Surface recreation in visible Waydroid**

Run: `./gradlew connectedDebugAndroidTest assembleDebug`

Expected: PASS.

Inside the Gamescope-hosted Waydroid window, rotate once and return to landscape. Expected: the black game surface remains alive and the launcher does not crash.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/dd1android/launcher/game app/src/main/java/io/github/dd1android/launcher/xserver app/src/main/java/io/github/dd1android/launcher/xconnector app/src/main/cpp app/src/main/AndroidManifest.xml app/build.gradle.kts NOTICE
git commit -m "feat: embed X server and Android game surface"
```

### Task 5: Fresh-process Linux runners and graphics bridges

**Files:**
- Create: `runtime/box64` as a pinned git submodule
- Create: `app/src/main/cpp/dd1_runner_main.c`
- Create: `app/src/main/cpp/dd1_linker.c`
- Create: `app/src/main/cpp/dd1_emulation.c`
- Create: `app/src/main/cpp/dd1_runtime_jni.c`
- Create: `app/src/main/java/io/github/dd1android/launcher/runtime/GameRunner.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/runtime/LaunchResult.java`
- Create: `app/src/main/assets/runtime/arm64-v8a/libs.tar.xz`
- Create: `app/src/main/assets/runtime/x86_64/libs.tar.xz`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/io/github/dd1android/launcher/runtime/GameRunnerCommandTest.java`

**Interfaces:**
- Produces: `GameRunner.launch(LaunchConfig, Surface): CompletableFuture<LaunchResult>`.
- `LaunchResult` contains exit code, renderer, first-frame flag, elapsed time, and log directory.

- [ ] **Step 1: Write failing runner command tests**

```java
@Test public void arm64SelectsBox64Runner() {
    RunnerCommand c = RunnerCommand.forAbi("arm64-v8a", config);
    assertEquals("libdd1_runner.so", c.packagedExecutable());
    assertEquals(config.executable().toString(), c.guestExecutable());
}

@Test public void x86WaydroidSelectsGlibcLoader() {
    RunnerCommand c = RunnerCommand.forAbi("x86_64", config);
    assertTrue(c.arguments().contains("ld-linux-x86-64.so.2"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*GameRunnerCommandTest'`

Expected: FAIL because runner classes do not exist.

- [ ] **Step 3: Implement the native runners**

Pin the RimDroid Box64 fork revision in the git submodule. Package the fresh-process launcher as `libdd1_runner.so` so Android extracts it executable under `nativeLibraryDir`. ARM64 loads Box64; x86_64 invokes the bundled x86_64 glibc loader directly. Both connect to the embedded X socket and write stdout/stderr to the session log.

Add Zink/Mesa libraries and MobileGlues as separately attributed runtime assets. The runner reports first frame through a pipe; after 20 seconds without a frame, `GameRunner` terminates the process and retries once with `fallbackRenderer`.

- [ ] **Step 4: Build both ABIs and run a synthetic ELF smoke test**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: APK contains `lib/arm64-v8a/libdd1_runner.so` and `lib/x86_64/libdd1_runner.so`.

Run the bundled synthetic GL-window test in visible Waydroid. Expected: one colored frame appears and exit code is 0.

- [ ] **Step 5: Commit**

```bash
git add .gitmodules runtime/box64 app/src/main/cpp app/src/main/java/io/github/dd1android/launcher/runtime app/src/main/assets/runtime app/build.gradle.kts NOTICE
git commit -m "feat: run Linux x86_64 payloads with Android graphics"
```

### Task 6: Touch input and AAudio bridge

**Files:**
- Create: `app/src/main/java/io/github/dd1android/launcher/input/TouchMapper.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/input/TouchOverlayView.java`
- Create: `app/src/main/cpp/alsa_aaudio_shim.c`
- Modify: `app/src/main/java/io/github/dd1android/launcher/game/GameActivity.java`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Test: `app/src/test/java/io/github/dd1android/launcher/input/TouchMapperTest.java`
- Test: `app/src/androidTest/java/io/github/dd1android/launcher/game/GameInputTest.java`

**Interfaces:**
- Produces: `TouchMapper.map(MotionEvent, Rect gameBounds): List<XInputEvent>`.
- Produces: ALSA symbols used by the downloaded FMOD libraries, backed by one AAudio stream.

- [ ] **Step 1: Write failing coordinate tests**

```java
@Test public void mapsLetterboxedTouchToGamePixels() {
    Rect bounds = new Rect(100, 0, 1180, 720);
    XInputEvent event = mapper.down(640, 360, bounds, 1280, 720);
    assertEquals(640, event.x());
    assertEquals(360, event.y());
}

@Test public void ignoresTouchesOutsideGameBounds() {
    assertTrue(mapper.downEvents(20, 20, new Rect(100, 0, 1180, 720)).isEmpty());
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*TouchMapperTest'`

Expected: FAIL because input classes do not exist.

- [ ] **Step 3: Implement minimal controls and audio**

Map tap to absolute motion plus left-button press/release. Add optional Escape, Space, Up, and Down buttons. Map Android Back to an Android pause sheet.

Implement only ALSA functions observed through `nm -D`/runtime logs, with `snd_pcm_writei` writing to AAudio. Use 48 kHz stereo signed 16-bit PCM where FMOD accepts it; log the negotiated format and buffer underruns.

- [ ] **Step 4: Test in visible Waydroid**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest`

Expected: PASS; touch markers reach the X client and the AAudio shim synthetic tone plays without underruns.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/dd1android/launcher/input app/src/main/java/io/github/dd1android/launcher/game app/src/main/cpp app/src/test app/src/androidTest
git commit -m "feat: bridge Android touch and audio to DD1 runtime"
```

### Task 7: DD1 launch, saves, fallback, and diagnostics

**Files:**
- Create: `app/src/main/java/io/github/dd1android/launcher/logs/SessionLogs.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/logs/LogExporter.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/saves/SaveLocator.java`
- Create: `app/src/main/java/io/github/dd1android/launcher/saves/SaveSnapshot.java`
- Modify: `app/src/main/java/io/github/dd1android/launcher/LauncherActivity.java`
- Modify: `app/src/main/java/io/github/dd1android/launcher/runtime/GameRunner.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/io/github/dd1android/launcher/logs/SessionLogsTest.java`
- Test: `app/src/test/java/io/github/dd1android/launcher/saves/SaveSnapshotTest.java`

**Interfaces:**
- Produces: `SessionLogs.start(AppPaths): SessionLogDir` and retention of five sessions.
- Produces: `SaveSnapshot.create(Path saves, Path snapshots): Path` using temporary directory plus atomic rename.

- [ ] **Step 1: Write failing retention and snapshot tests**

```java
@Test public void retainsFiveNewestSessions() throws Exception {
    for (int i = 0; i < 7; i++) SessionLogs.start(paths);
    assertEquals(5, Files.list(paths.logs()).count());
}

@Test public void snapshotNeverReplacesSource() throws Exception {
    Files.writeString(paths.saves().resolve("persist.game.json"), "owned-save");
    Path snapshot = SaveSnapshot.create(paths.saves(), paths.snapshots());
    assertEquals("owned-save", Files.readString(snapshot.resolve("persist.game.json")));
    assertEquals("owned-save", Files.readString(paths.saves().resolve("persist.game.json")));
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*SessionLogsTest' --tests '*SaveSnapshotTest'`

Expected: FAIL because logging and save types do not exist.

- [ ] **Step 3: Integrate the owned DD1 payload**

Add Play only when validation passes. Launch from `files/game`, redirect SDL preferences into app-private directories, locate the created save tree after exit, and preserve it outside replaceable payload storage. Retry once with MobileGlues only when Zink produces no first frame. Export launcher, native runner, Box64, renderer, and game logs through a `FileProvider` ZIP.

- [ ] **Step 4: Run the end-to-end acceptance sequence**

In the visible Gamescope-hosted Waydroid window:

1. Install the debug APK.
2. Import `/home/ljh/.local/share/Steam/steamapps/common/DarkestDungeon` with `scripts/import-local-game.sh`.
3. Press Play.
4. Reach the title screen with audio.
5. Create a campaign using touch.
6. Complete one combat.
7. Exit, relaunch, and load the same campaign.
8. Export logs and verify the ZIP contains launcher, runner, renderer, and game logs.

Repeat steps 3-8 on the Galaxy S25 ARM64 build before marking the runtime MVP complete.

- [ ] **Step 5: Run all automated checks**

Run: `./gradlew clean testDebugUnitTest connectedDebugAndroidTest assembleDebug`

Expected: BUILD SUCCESSFUL with both ABI libraries packaged.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: complete DD1 Android runtime MVP"
```

### Task 8: Runtime MVP documentation

**Files:**
- Create: `README.md`
- Create: `docs/DEVELOPMENT.md`
- Create: `docs/KNOWN_ISSUES.md`
- Modify: `NOTICE`

**Interfaces:**
- Produces: reproducible developer build, visible Waydroid test, legal payload import, and diagnostic instructions.

- [ ] **Step 1: Write documentation checks**

Run:

```bash
test -s LICENSE
test -s NOTICE
test -s README.md
test -s docs/DEVELOPMENT.md
test -s docs/KNOWN_ISSUES.md
! rg -n '/home/ljh|DarkestDungeon/.+\.(png|bank|json)$' app/src/main/assets README.md NOTICE
```

Expected: the first run fails because the documents do not exist.

- [ ] **Step 2: Document only verified behavior**

README must state ownership requirements, no bundled game data, supported Android floor, current runtime status, and GPL license. DEVELOPMENT must contain exact SDK/NDK/build/Gamescope/Waydroid commands. KNOWN_ISSUES must list only failures reproduced during Tasks 1-7.

- [ ] **Step 3: Run verification**

Run the documentation checks above, then `./gradlew clean testDebugUnitTest assembleDebug`.

Expected: all checks pass.

- [ ] **Step 4: Commit**

```bash
git add README.md NOTICE docs
git commit -m "docs: document runtime MVP build and limitations"
```
