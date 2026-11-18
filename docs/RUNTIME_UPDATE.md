# Updating the Winlator runtime

The launcher keeps Winlator's tree byte-identical to upstream so a new release
can be dropped in without merging.

## What belongs to us

- `app/src/*/java/com/winlator/dd1/**`
- `app/src/main/res/**/dd1_*.xml`
- `app/AndroidManifest.xml` (launcher activity, install service, application class)
- `app/build.gradle` (Steam dependencies, packaging rules)
- `docs/**`

Everything else under `app/src/main` — Java, resources, assets, `cpp`, `jniLibs` —
is upstream Winlator and carries no local edits, except the two listed below.

## Upstream edit 1: the touch overlay

`XServerDisplayActivity` gains two lines that attach the launcher's touch
overlay, immediately after `rootView.addView(touchpadView)`:

```java
// dd1: the game is played by touching it, so direct touch sits over the
// runtime's relative cursor and hands anything but one finger back.
rootView.addView(new com.winlator.dd1.DD1TouchOverlay(this, xServer, touchpadView));
```

The same file also skips the runtime's "Starting up" box for a launch the
launcher asked to cover, where `preloaderDialog.show(R.string.starting_up)` was:

```java
// dd1: a launch the launcher started has its own cover until the game draws.
if (!com.winlator.dd1.DD1LaunchCover.coversStartup(this)) preloaderDialog.show(R.string.starting_up);
```

The box is a dialog, a window of its own, so nothing in the activity's views can
sit above it. The cover itself (`DD1LaunchCover`) is placed by the overlay and
needs nothing else from this file. The "Updating system files" box and launches
without the launcher's extra - the runtime's file manager - keep the box.

Nothing else in that file changes. The overlay needs a view to attach to and the
`XServer` to inject into, and the runtime offers no hook for either, so this is
the smallest place to say it. Keep it to these lines; everything the touch
profile does belongs in `com.winlator.dd1`.

## Upstream edit 2: the build that installs beside Winlator

`./gradlew assembleRelease -PbesideWinlator` builds the launcher as
`com.dd1.wine` instead of `com.winlator`, so it can be installed next to
Winlator or a fork of it. The name has to be exactly as long as `com.winlator`,
because the runtime's files name their home in fixed-size fields; it is written
over the old one byte for byte as they are unpacked. That needs these edits,
each marked `dd1`:

- `core/TarCompressorUtils.java`, in the private `extract`: the file output stream
  is wrapped in `com.winlator.dd1.DD1Relocation.wrap(...)`, and a symlink's target
  goes through `DD1Relocation.link(...)`. Every runtime archive - rootfs, its
  patches, box64, the graphics drivers, PulseAudio, the container pattern - is
  unpacked through here, so this is the one place that sees all of them.
- `core/AppUtils.java`: `INTERNAL_STORAGE` is built from
  `BuildConfig.APPLICATION_ID` instead of naming `com.winlator`.
- `core/FileUtils.java`: the FileProvider authority is
  `activity.getPackageName() + ".FileProvider"`, matching the manifest's
  `${providerAuthority}`. A literal authority is also what makes two
  `com.winlator`-derived apps refuse to install together.
- `cpp/gladiorenderer/include/gladio.h`, `cpp/vortekrenderer/include/vortek.h`,
  `cpp/winlator/include/winlator.h`: each path define is wrapped in
  `#ifdef DD1_BESIDE_WINLATOR`, which `app/build.gradle` passes as a C flag.

The standard build is unchanged by all of it: `DD1Relocation` hands the stream
back untouched when the id is `com.winlator`, and the defines fall through to
upstream's. Switching between the two builds reconfigures CMake; if a native
library still carries the other path, delete `app/.cxx` and build again.

## Upstream edit 3: how the renderers wait on their ring buffers

`cpp/winlator/include/events.h`, `busyWait`, marked `dd1`. The guest's GL and
Vulkan calls reach Gladio and Vortek through shared-memory ring buffers, and
each side waits for the other there hundreds of times a frame. Upstream gives
the core away with `thrd_yield()` on every turn; under the EEVDF scheduler of
Linux 6.6 and later (the Galaxy S26 Ultra runs 6.12) a yielding thread waits
behind anything else runnable on its core, and Darkest Dungeon dropped to
~22 FPS on screens it draws at ~200 when nothing else was awake (issue #5).
On arm64 the wait now spins on `isb` for up to 50 microseconds, timed by the
generic timer, then sleeps 100 microseconds per turn as upstream did after its
500 yields. Other architectures keep upstream's loop.

Only the renderer side lives in this repository. The guest side of the same
handshake is Gladio's `libGL` in the rootfs archive, a binary that still
yields.

## Procedure

1. Check out the new upstream release next to this repository.
2. Replace every path listed as upstream above with the release's version.
3. Restore `app/AndroidManifest.xml` and `app/build.gradle` from this repository
   and re-apply upstream's own changes to them by hand; both files are small.
4. Re-apply the touch overlay and launch cover lines to `XServerDisplayActivity`, and the
   side-by-side edits; see above. Search the new release for
   `/data/data/com.winlator` and `"com.winlator` - a new hard-coded path is
   one more place the side-by-side build has to be told about.
5. Check the `ignoreAssetsPattern` in `app/build.gradle` against the new
   release's `assets/`. It leaves out the runtime parts the game profile never
   asks for (Direct3D layers, native Windows components, the MIDI sound font);
   a renamed asset slips back in, and a profile that starts asking for one of
   them needs it taken off the list.
6. Build, run the unit tests, then launch the game once on a device and check
   that a tap still lands where it was aimed.

## Rule

Do not edit upstream files beyond the two edits above. If the launcher needs behaviour the runtime does not
offer, add it in `com.winlator.dd1` and use the runtime's existing entry points:
`XServerDisplayActivity` accepts `container_id` and `exec_path` intent extras, and
containers carry per-game settings.
