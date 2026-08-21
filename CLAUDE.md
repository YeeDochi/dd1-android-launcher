# DD1 Android Launcher

An Android launcher that installs and runs the owner's copy of Darkest Dungeon
through the Winlator runtime. The user signs in to Steam, the launcher downloads
the Windows build they own, and one Play button starts the game.

The game runs today on a Galaxy S25. Touch input, save synchronisation and the
mod manager all work and have been exercised on real devices.

## Boundaries

- **No game data ships here.** No Darkest Dungeon files, art or FMOD binaries in
  the APK or the repository. Store artwork is fetched at runtime into the cache
  directory only.
- **Going public is the owner's call, and only the owner's.** The repository is
  private. Never change its visibility, publish a release, or push anywhere
  public without being asked for that specific act.
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
  `connectedAndroidTest` against the phone: Gradle uninstalls the app afterwards
  and takes the 3.7 GB install and the Wine prefix with it.
- **Waydroid for instrumentation tests and UI checks.** It cannot run the game -
  it is x86_64, and the ARM64 runtime dies in Houdini.
- Wireless debugging drops when the phone's Wi-Fi sleeps; USB is steadier.

## Commands

```
./gradlew assembleDebug testDebugUnitTest     # what every change must pass
./gradlew connectedDebugAndroidTest           # Waydroid only, never the phone
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
- DLC arrives in its own Steam package, so ownership is read across all packages.
- Newer DLC keeps its store artwork behind a hashed path; resolve it through
  `store.steampowered.com/api/appdetails`.

## Where things are written down

- `docs/superpowers/specs/2026-08-19-dd1-android-launcher-design.md` - the design
- `docs/superpowers/specs/2026-08-19-dd1-launcher-features.md` - input, mods,
  install management, payload checks
- `TODO.md` - the working task list, untracked
