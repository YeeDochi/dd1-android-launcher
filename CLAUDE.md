# DD1 Android Launcher

An Android launcher that installs and runs the owner's copy of Darkest Dungeon
through the Winlator runtime. The user signs in to Steam, the launcher downloads
the Windows build they own, and one Play button starts the game.

The game runs today on a Galaxy S25 and on a Galaxy Tab S8 (Adreno 730, which
lands on `turnip,gladio`). Touch input, save synchronisation and the mod manager
all work and have been exercised on both. That is still two Adreno devices: a
report of a black picture on newer hardware turned out to be the runtime picking
a driver by model number, and what an Exynos or Mali device actually draws has
never been seen - the Xclipse fix in 0.1.8 is reasoned, not witnessed.

## Boundaries

- **No game data ships here.** No Darkest Dungeon files, art or FMOD binaries in
  the APK or the repository. Store artwork is fetched at runtime into the cache
  directory only.
- **Publishing is the owner's call, and only the owner's.** The repository is
  public, so a push is world-readable the moment it lands: push, change the
  repository's visibility, or publish a release only when asked for that specific
  act. Read a commit message and a doc as something strangers will read.
- **Do not call this a port** - it is a launcher on the Winlator runtime, and the
  wording matters. Red Hook's EULA reserves derivative works and an official iPad
  edition exists, so what keeps this defensible is that it ships no game data,
  modifies no game binary, carries no Red Hook trademark in its name, and earns
  nothing. Keep every one of those true.
- Winlator is under its own licence; keep `THIRD_PARTY_NOTICES` and attribution
  intact.

## Layout

Everything under `app/src/main` is upstream Winlator and carries **no local
edits**, except:

- `app/src/*/java/com/winlator/dd1/**` - the launcher
- `app/src/main/res/**/dd1_*.xml` - the launcher's resources
- `app/AndroidManifest.xml`, `app/build.gradle` - project files
- `XServerDisplayActivity` - two lines that attach the touch overlay, and
  nothing else; `docs/RUNTIME_UPDATE.md` carries them verbatim

`docs/RUNTIME_UPDATE.md` describes how to take a new Winlator release. If the
launcher needs behaviour the runtime does not offer, add it in
`com.winlator.dd1` and use the runtime's entry points: `XServerDisplayActivity`
takes `container_id` and `exec_path` intent extras, and containers carry the
per-game settings.

## Constraints that look wrong but are not

- **`applicationId` must stay `com.winlator`.** The Winlator root filesystem has
  `/data/data/com.winlator/...` baked into box64's ELF interpreter, wineserver,
  ntdll and `ld.so.cache`, in fixed-size fields. A different id needs a
  same-length one (<=12 chars) plus a rewrite pass over the extracted tree.
- **`targetSdkVersion` must stay 28.** Android 29+ forbids executing files in the
  app data directory, and the runtime unpacks box64 and Wine there. Android shows
  a compatibility warning for this; it is expected.
- The game is launched through the stock Winlator path (winhandler, `/dir`).
  Earlier workarounds around that path were chasing a corrupt install and are
  gone.

## Working with devices

- **Phone (`adb install -r`) for running the game.** Never run
  `connectedAndroidTest` while the phone is attached: it runs on *every* attached
  device and uninstalls the app afterwards, taking the 3.7 GB install, the Wine
  prefix and the saves with it. It does that after a green test run, so nothing in
  the output warns you. With both devices on adb, drive the tests yourself:

  ```
  ./gradlew assembleDebug assembleDebugAndroidTest
  adb -s <waydroid> install -r app/build/outputs/apk/debug/app-debug.apk
  adb -s <waydroid> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb -s <waydroid> shell am instrument -w com.winlator.test/androidx.test.runner.AndroidJUnitRunner
  ```

  That prints `OK (N tests)` and uninstalls nothing. Name `-s` on every adb call;
  a bare `adb install` with two devices attached is the same hazard.
- **Waydroid parks the container** in `Container: FROZEN` a minute or two after it
  has nothing to draw. `adb devices` still lists it as `device`, `adb shell`
  hangs, and Gradle reports `No compatible devices connected` - which reads like
  an ABI mismatch and sends you after the wrong thing. `waydroid session stop`,
  `waydroid session start`, wait ~20s, reconnect, then run immediately. A run
  against a container on its way to freezing can also produce *bogus failures*:
  once it reported 16 CPUs and a stale preset, and both went green on an
  unchanged tree after a restart.
- **Waydroid for instrumentation tests and UI checks.** It cannot run the game -
  it is x86_64, and the ARM64 runtime dies in Houdini.
- Wireless debugging drops when the phone's Wi-Fi sleeps; USB is steadier.

## Commands

```
./gradlew assembleDebug testDebugUnitTest     # what every change must pass
./gradlew connectedDebugAndroidTest           # only with the phone unplugged
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell monkey -p com.winlator -c android.intent.category.LAUNCHER 1
adb -s <serial> shell run-as com.winlator ls files    # game, staging, rootfs
```

`MainActivity.DEBUG_MODE` routes Wine and box64 output to logcat; it is off in
committed code.

## Hard-won facts

- **Check payloads by content, not size.** A 3.7 GB install once consisted
  entirely of zeros because `AppItem` was built with `verify = true`, which
  allocates and validates files without downloading them. Wine answered
  `ERROR_BAD_EXE_FORMAT` and every symptom above it looked like a runtime fault.
  Read the first bytes (`MZ`) before believing an install.
- DD1 is an OpenGL game; `err:wgl:egl_init` in the log is harmless.
- DLC arrives in its own Steam package, so ownership is read across all packages
  - and the packages name other games too, so **which appids are this game's DLC
  is read from the game's own PICS depot table** (`DD1DepotCatalog.dlcAppIds`),
  never from a list in the source. A five-entry list is why The Musketeer
  (`445700`, free, so everybody owns it) was never installed for anyone.
- Newer DLC keeps its store artwork behind a hashed path; resolve it through
  `store.steampowered.com/api/appdetails`.
- **JavaSteam's `SteamCloud` throws away the `EResult`.** `beginAppUploadBatch`,
  `beginFileUpload` and `commitFileUpload` all build their answer out of the
  response body alone, so a refused begin looks like an upload with no blocks and
  a refused commit says only "not committed". Call the `Cloud` service directly
  when a cloud call fails for no visible reason; that is how `DuplicateRequest`
  was found after two wrong theories.
- **Steam answers a file it already holds at the same digest with
  `DuplicateRequest`.** There is nothing to send, which is the same thing as
  having sent it. Read as a failure it stopped the whole save batch at the first
  unchanged file.
- **Steam Guard takes one of two roads and JavaSteam prefers the phone.** With
  `acceptDeviceConfirmation()` answering true, an account with the mobile
  authenticator always goes to the push and never sees the code box; the box is
  for accounts Steam mails a code to. To exercise that path you have to remove the
  authenticator from the test account.
- **Sharing `com.winlator` means the launcher cannot be installed beside
  Winlator.** Android sees one package name signed two ways and refuses with
  "앱이 설치되지 않았습니다" and no reason. That is what a Fold 8 install failure
  turned out to be, after targetSdk, ABI, signature scheme and 16 KB alignment
  had each been ruled out - the owner had Winlator installed. Forks count too:
  the FileProvider authority is the literal `com.winlator.FileProvider` in
  upstream's manifest and code, so a fork that renamed its id and kept the
  authority collides as well. Renaming is possible - `com.winlator` is 12
  characters and the extracted tree holds the path in 165 rootfs files, one
  box64 binary and one patch file, so a same-length id is a byte-for-byte
  replacement with no ELF surgery - but the new id is a new app to Android, and
  no app can read another's data directory: every install would start again from
  a 3.7 GB download.
- **Turnip is the Adreno driver whatever the number after it says.** The runtime
  picks it by matching `adreno[^678]*([678][0-9]{2})`, so a 9-series, a
  four-digit model or the X-series naming reads as "not an Adreno" and gets
  Vortek, on a profile tuned for Turnip. The launcher decides on the name
  (`DD1GraphicsDriver`) and corrects a profile made before that.

## Where things are written down

- `docs/superpowers/specs/2026-08-19-dd1-android-launcher-design.md` - the design
- `docs/superpowers/specs/2026-08-19-dd1-launcher-features.md` - input, mods,
  install management, payload checks
- `docs/RELEASE_NOTES.md` - what shipped in each release, newest first; the text
  is pasted into the GitHub release body. Korean from 0.1.3 on
- `TODO.md` - the working task list, untracked

`AGENTS.md` is a symlink to this file. It was a copy once and drifted into saying
touch input and save synchronisation were still missing.
