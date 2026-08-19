# DD1 Launcher Remaining Features

Companion to `2026-08-19-dd1-android-launcher-design.md`. It covers the parts that
the design left open and the lessons the first working launch produced.

## Installed-content management

The launcher owns roughly 4 GB of downloaded content and a 550 MB Wine prefix. A
user needs to reclaim that space and to recover from a bad install without
reinstalling the app.

Home exposes `게임 파일 삭제` only while the game is installed. Confirmation states
the download size that will be required again. Deleting removes `files/game` and
`files/staging`, leaves the runtime, the Wine prefix and the stored Steam token
alone, and returns Home to the download state.

`런타임 초기화` is a separate, later action because the prefix holds the save files.
It is not offered until save synchronization works.

## Download progress

The first install reported `0 B` throughout because progress came only from
`onChunkCompleted`, and the allocation phase never calls it.

- `onStatusUpdate` and `onFileCompleted` set the current file and keep the phase
  visible; they must not reset the byte counters.
- `onChunkCompleted` carries `percent` per depot, not for the whole app, so the
  overall bar is driven by completed depots plus the running depot's percent.
- Downloaded bytes accumulate from the callback deltas; the total is only an
  estimate until the first depot completes, and the bar stays indeterminate
  until an estimate exists.
- Percent, transferred size, and speed always render together or not at all.

## Payload verification

The first 3.7 GB install consisted entirely of zero-filled files: `AppItem` was
constructed with `verify = true`, which allocates and checks files without ever
fetching their contents. Wine rejected the result with `ERROR_BAD_EXE_FORMAT`,
and every symptom above it looked like a runtime fault.

Validation therefore checks content, not just presence and size:

- The launch executable starts with `MZ` and its PE header names a supported
  machine type.
- A required data file is non-empty.
- Failing validation reports the offending path and offers re-download, never a
  silent retry.

## Touch controls

DD1 is a mouse-driven game at a fixed internal resolution. The launcher ships one
DD1 profile instead of exposing Winlator's editor:

- Tap is left click, long press is right click, drag scrolls the raid and town
  views, and two-finger drag pans.
- A single on-screen key row covers Esc, Space, and the map toggle.
- Pinch zoom is not bound; DD1 has no zoom.
- The profile is editable only after the built-in one proves insufficient.

## Save location

Saves live in the Wine prefix under the user's Documents directory, in
`Darkest/profile_<number>`. Cloud synchronization uses that path; the design's
conflict rules apply unchanged.

## Workshop and mods

Deferred until installation and cloud sync are reliable, per the design. When it
lands, mods are downloaded through Steam Workshop into `files/game/mods`, and the
launcher writes the load order the game reads. Mod code never runs in the
launcher process.

## Release gate

Publication stays blocked until Red Hook answers the written request described in
the design. Nothing in this document changes that.
