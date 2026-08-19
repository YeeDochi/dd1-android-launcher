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

The game must be played by touching what you want, not by driving a cursor or a
virtual pad. DD1 does ship a controller layout, and a physical pad still works
through the runtime, but a virtual pad turns the phone into a console emulator
and is not the default.

Direct manipulation covers almost everything:

- Tap where you want to act; the click lands at that point.
- Drag with one finger to move an item, a trinket, or a hero in the formation,
  which is what DD1's own mouse drag already is.
- Two-finger drag scrolls the estate, the dungeon and long lists.
- Two-finger tap is right click.

The two things touch cannot express get explicit answers.

**Hover.** Skill, item, quirk and trinket descriptions only exist under a resting
cursor. A long press parks the cursor without clicking, so the tooltip appears;
the cursor sits slightly above the fingertip in this mode so the text is not
covered. Releasing does not click.

**Keyboard.** Menu and shortcut keys are not typed. They appear as a small row of
on-screen buttons along one edge, built from the game's own keyboard controls
screen and confirmed in game. The row can be collapsed. Nothing requires an
on-screen keyboard.

Everything above is a starting profile. It gets checked against the screens that
actually hurt: the estate, the loot window, the formation row, and combat skill
selection, at whichever internal resolution reads best on the S25.

## Save location

Saves live in the Wine prefix under the user's Documents directory, in
`Darkest/profile_<number>`. Cloud synchronization uses that path; the design's
conflict rules apply unchanged.

## Mod manager

Two kinds of mods share one list: Workshop items the user subscribed to, and
local mods the user copied in. The launcher owns both because no Steam client
runs on the device to deliver Workshop content.

Every mod appears with its title, source, version marker, and enabled state.
Users enable, disable, reorder, and delete. The launcher writes the load order
file the game reads and never executes mod code itself.

### Workshop delivery

Steam does not push Workshop content to a device with no Steam client, so the
launcher fetches it the same way it fetches the game: through the CDN.

1. List the account's subscribed items for App ID `262060`.
2. Read each item's details for title, `time_updated`, and its manifest.
3. Download the manifest's chunks from a CDN server, verifying each chunk hash
   and then each file hash.
4. If the payload is a single archive, unpack it; otherwise use the file tree as
   downloaded.
5. Swap it into `files/game/mods/<published_file_id>` through staging, and record
   `time_updated`.

An item without a manifest is skipped with a stated reason rather than failing
the whole sync. A user may also paste a Workshop item URL, which resolves the id
and runs the same steps. Items that declare required items offer to fetch those
too; declining leaves the mod installed but flagged.

### Sync plan

Synchronization first produces a plan the user can read, then applies it. Each
item lands in exactly one bucket:

- **Install** — subscribed, not present locally.
- **Update** — present, and the subscription's `time_updated` is newer.
- **Disabled update** — an update for a mod the user turned off; fetched, still
  off afterwards.
- **Orphan** — installed from Workshop but no longer subscribed. Never deleted
  automatically; the user decides.
- **Stale entry** — a config row whose mod directory is gone.
- **Conflict** — the same mod id exists both from Workshop and from a manual
  copy. Both versions are shown and the user picks; the choice is remembered so
  the next sync does not ask again.
- **Skipped** — anything unusable, always with a reason.

Nothing is applied while the game is running, and a failed download leaves the
previous version in place.

### Mod configuration

A single versioned config file holds one row per mod: id, source, enabled flag,
and position. It is reconciled against a scan of the mods directory on every
launch, so mods added or removed outside the launcher appear correctly. The load
order the game reads is written from that list, and it is the only file the
launcher writes inside the install.

### Boundaries

- Archives are unpacked with path traversal rejected and a size ceiling.
- Deleting the game files also deletes `files/game/mods`, stated in the delete
  confirmation.
- Mod code never runs in the launcher process.

## Release gate

Publication stays blocked until Red Hook answers the written request described in
the design. Nothing in this document changes that.
