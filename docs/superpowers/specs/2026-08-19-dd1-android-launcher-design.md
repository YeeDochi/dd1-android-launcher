# DD1 Android Launcher Design

## Purpose

Build a GPL-3.0, Darkest Dungeon 1-specific Android client that lets a player authenticate with Steam, download only game and DLC files they own, and run the official Linux x86_64 build locally. The APK and source repository must never contain Darkest Dungeon executables, assets, DLC, credentials, or decrypted Steam content.

The user experience is intentionally narrower than a general PC emulator: install the APK, sign in, install, and press Play. Runtime, graphics, input, and save paths are selected automatically for Darkest Dungeon.

## Success criteria

The first runnable milestone must:

1. Build and install on Android 11 or later.
2. Be observable in a Gamescope-hosted Waydroid window during development.
3. Accept a user-owned local Linux installation through a developer-only ADB import path.
4. Validate the payload without modifying the source Steam installation.
5. Launch `_linuxnosteam/darkest.bin.x86_64` without Wine or Termux.
6. Display the title screen, accept touch input, play audio, create a campaign, complete one combat, and persist the save.
7. Export one diagnostic archive containing launcher, Box64, renderer, and game logs.

Steam login, on-device Depot download, DLC selection, Workshop, cloud saves, and launcher self-update follow as separate milestones only after the local-payload runtime works.

## Supported systems

- Minimum target: Android 11, ARM64, 6 GB RAM, Vulkan 1.1, Snapdragon 865-class performance.
- Development device: Galaxy S25.
- Development emulator/container: Waydroid x86_64 with Mesa/Radeon.
- Initial GPU policy: Adreno via Zink/Vulkan; MobileGlues/OpenGL ES is the automatic fallback.
- Mali and other GPUs are accepted when the fallback works, but are not release blockers for the first ARM64 milestone.
- The app uses `minSdk 30`, `compileSdk 35`, `targetSdk 35`, NDK `27.0.12077973`, Java 17, and 16 KiB-compatible native linking.

Waydroid is a functional and UI test environment, not a mobile performance benchmark. The project provides an x86_64 development flavor for Waydroid and an ARM64 release flavor for phones.

## Legal and licensing boundary

- Project license: GPL-3.0-or-later.
- Reused RimDroid GPL code remains attributed in `NOTICE` and source headers.
- Box64, JavaSteam, DepotDownloader, Mesa, MobileGlues, SDL-related code, and linker helpers retain their upstream licenses and notices.
- StS2 Launcher/Mod Manager code may be selectively adapted under its MIT license with attribution; it is not a project base or Git fork.
- No Red Hook, Darkest Dungeon, Steam, FMOD, or DLC binary/data is committed or packaged.
- The developer import script copies from a path the user supplies and is excluded from release behavior.
- Steam credentials are sent only to Steam. Persistent refresh tokens, once implemented, are encrypted by Android Keystore.

## Architecture

### Android application

Use a single standard Android application with Java/Kotlin UI and a native runtime. Do not use Godot, Wine, Termux, a second APK, or an external X server.

The app has four visible states:

1. No payload: explains that an owned copy is required and offers Install/Import.
2. Installing: shows deterministic byte and file progress.
3. Ready: provides Play, repair, settings, saves, logs, and later Mods.
4. Running: full-screen game surface with an optional touch-control overlay and a small exit/settings affordance.

### Payload layout

Store mutable data below app-private storage:

```text
files/
  game/                 # official Steam payload
  runtime/              # extracted, versioned runtime support files
  saves/                # stable save root, outside game payload
  mods/                 # installed Workshop/manual mods
  cache/                 # Steam chunks, thumbnails, Box64 dynacache
  logs/                  # current and previous launch logs
```

`game/` is replaceable. Updates must not delete `saves/`, `mods/`, or `logs/`.

### Runtime process

Use a fresh executable process packaged through Android's native library directory. This avoids executing downloaded code directly from app data and keeps ART state out of the game address space.

- ARM64: the packaged runner loads Box64 and maps the downloaded Linux x86_64 executable and libraries.
- Waydroid x86_64: the packaged x86_64 runner invokes the bundled glibc loader directly and uses the same X11, graphics, audio, input, and logging contracts.
- Both runners receive only an explicit environment allowlist and absolute app-private paths.
- The game working directory is `files/game`.
- The selected executable is `_linuxnosteam/darkest.bin.x86_64`; the Steam API build is not used for the first milestone.

### Graphics and presentation

Embed the X server used by the RimDroid/Winlator lineage and present it in a full-screen Android `SurfaceView`.

- Default ARM64 renderer: desktop OpenGL calls through Zink to Vulkan.
- Fallback renderer: MobileGlues translates desktop OpenGL to OpenGL ES.
- Waydroid renderer: Mesa through its host Radeon Vulkan/OpenGL stack.
- Start at 1280x720 internal resolution. Use integer-safe aspect fitting and black bars; do not stretch.
- Record renderer selection and fallback cause in logs.
- If startup reaches no first frame within 20 seconds, terminate cleanly and retry once with the fallback renderer.

### Input

Darkest Dungeon is mouse-oriented, so direct touch maps to absolute pointer movement plus left click. Long press maps to right click only where needed. Android Back opens the launcher pause sheet rather than being sent to the game.

The first milestone adds only four optional virtual keys: Escape, Space, Up, and Down. A full editable overlay and gamepad remapping are later work after gameplay proves which inputs are actually missing.

### Audio

Keep the official x86_64 FMOD libraries in the downloaded payload. Route their Linux ALSA output through a minimal ALSA-to-AAudio shim. Do not bundle proprietary FMOD Android libraries or SDK files.

The audio shim exposes only the functions observed from the downloaded FMOD build. Unsupported calls fail explicitly and are logged. Audio failure must not corrupt saves or leave the runtime process alive.

### Saves

Set `HOME` and XDG variables to app-private paths so SDL and the game resolve preferences predictably. Discover the actual save path from the first successful run, then bind it to `files/saves/` without rewriting save content.

Every game update and future cloud sync begins with an atomic local save snapshot. Cloud behavior is out of the first implementation plan.

### Steam and DLC milestone

Use JavaSteam 1.8.0 and `javasteam-depotdownloader` 1.8.0 in the Android process. The app authenticates with QR/Steam Mobile or Steam Guard, verifies App ID `262060`, resolves the Linux branch manifests, and downloads owned depots into a staging directory. A completed staging tree replaces `game/` only after payload validation succeeds.

DLC ownership and content are derived from Steam metadata and downloaded depots. The launcher does not unlock DLC, edit DLC checks, or synthesize ownership state.

### Workshop and mods milestone

Workshop support follows the StS2 client's user-facing flow but uses DD1's data-mod layout:

- browse/search item metadata;
- subscribe and download through Steam;
- validate the mod directory;
- enable/disable by moving the mod between active and disabled directories;
- preserve load order;
- never edit Workshop source content in place.

No DLL patching or Harmony layer is planned unless a specific DD1 mod demonstrably requires it.

### Cloud milestone

Cloud sync is explicit. Before Play, compare local and cloud summaries. When both changed, require Keep Local or Keep Cloud; never choose silently. Reject empty or structurally abnormal uploads and snapshot local saves before each cloud operation.

## Error handling and diagnostics

- Every native launch writes a new timestamped log set.
- Keep the latest five sessions.
- Export logs as a ZIP through Android's share sheet.
- Installation uses staging plus atomic rename and can resume at a file boundary.
- A failed validation names the first missing or invalid required file.
- A runtime crash returns to the launcher with exit status, last renderer, and an Export Logs button.
- The app never retries Steam authentication or destructive save operations silently.

## Testing strategy

- Pure JVM tests cover path validation, payload detection, renderer selection, state transitions, and save snapshot rules.
- Native host tests cover environment construction and ELF header validation.
- Android instrumentation tests cover lifecycle, Surface recreation, permission-free app-private storage, and log export.
- Waydroid runs inside a visible Gamescope window for install, import, launch, rotation, suspend/resume, and touch tests.
- Galaxy S25 validates ARM64 Box64, Vulkan/Zink, audio, thermals, and sustained gameplay.
- A Snapdragon 865-class device is required before declaring the minimum specification verified.

## Delivery phases

1. Runtime MVP with developer-only local payload import.
2. Steam authentication, ownership, Depot/DLC download, update and repair.
3. Save manager and Steam Cloud.
4. Workshop browser and mod enable/disable/load order.
5. Compatibility expansion, controller editor, and performance profiles.
6. Butcher's Circus investigation; no support promise because Steam networking may be unavailable.

## Deliberate exclusions

- No game assets in APK or repository.
- No Windows/Wine path while the official Linux build works.
- No generic multi-game containers or per-game environment editor.
- No analytics, accounts, backend service, or telemetry.
- No custom updater until a signed release channel exists.
- No PvP implementation in the first four phases.
