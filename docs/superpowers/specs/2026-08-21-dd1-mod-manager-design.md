# DD1 Mod Manager Design

## Goal

Add a launcher screen that installs, updates, lists, and removes Darkest Dungeon
Workshop and local mods without running mod code or changing campaign saves.
The game remains responsible for enabling mods and choosing their per-campaign
load order.

## Scope

The first usable manager does four things:

1. Reads the signed-in Steam account's subscribed Workshop items for app
   `262060` and their title and `time_updated` metadata.
2. Reconciles that list with directories already under `files/game/mods` and
   shows install, update, current, orphan, local, and skipped states.
3. Applies an explicit sync through staging and retains the previous installed
   copy if any item fails.
4. Deletes a selected installed mod after confirmation.

Pasting an arbitrary Workshop URL, recursively offering required items, and
editing campaign load order are excluded. They are independent follow-ups and
are not needed to deliver subscribed items safely.

## Existing Code Reused

- `DD1SteamSession` remains the only Steam connection and exposes Workshop
  listing alongside its existing license, depot, and cloud operations.
- `DD1InstallService` remains the only foreground transfer service. A Workshop
  transfer cannot overlap a game or DLC download.
- `ModSyncPlan` continues to classify install, update, disabled update, current,
  orphan, and skipped items before disk changes occur. Until campaign editing
  exists, installed Workshop items are treated as enabled for plan purposes, so
  the disabled-update bucket remains dormant rather than being deleted.
- `javasteam`'s `PublishedFile.GetUserFiles` and `PublishedFile.GetDetails`
  provide the subscription and item metadata.
- `javasteam-depotdownloader`'s `PubFileItem` performs Workshop manifest lookup,
  CDN download, chunk verification, and payload delivery. No second downloader
  or archive implementation is added.

## Storage

Each installed Workshop item ends at:

```text
files/game/mods/<published_file_id>/
```

Each directory has a launcher-owned sidecar file named `.dd1-workshop` with
three UTF-8 lines: published-file id, `time_updated`, and title. The marker is
metadata only; the game ignores dotfiles. A directory with a valid marker is a
Workshop install. A directory without one is a local mod.

The existing `ModList` file is not written by this feature. Its assumed global
enable/order model does not match DD1's per-campaign behavior and remains unused
until campaign-save editing has a separately approved design.

## Steam Data Flow

After the existing Steam session reaches the owned/ready state, the Workshop
screen requests subscriptions. `GetUserFiles` is paged until it returns no more
items, constrained to app `262060` and the subscribed-list query. Item details
are requested in batches and converted to `ModSyncPlan.Subscribed` values.

The local scan reads only direct children of `files/game/mods`. Valid Workshop
markers become `ModSyncPlan.Installed` values; all other directories remain
visible as local mods. Missing content handles, unsupported Workshop file
types, malformed responses, and item-level metadata failures become skipped
rows with reasons instead of aborting the entire plan.

## Applying a Sync

Sync starts only from a visible button and only when the install service has no
active transfer. For each install or update:

1. Remove and recreate `files/workshop-staging/<id>`.
2. Submit one `PubFileItem(262060, id, ...)` targeting that staging directory.
3. Require a non-empty delivered directory containing `project.xml` or a single
   child directory containing `project.xml`; use that child as the payload.
4. Write `.dd1-workshop` in the payload.
5. Rename the current installed directory to a sibling backup, rename the
   payload into place, then delete the backup.
6. If promotion fails, restore the backup and report the item as failed.

Items are applied serially. A failure stops the current item but not the rest of
the selected plan. Orphans and local mods are never deleted by sync. Updates
are never started automatically, and the manager is inaccessible while the
game's runtime activity owns the foreground.

## Screen

The drawer gains one Workshop entry. Its screen follows the existing DLC and
save-screen structure: shared header, scrollable rows, progress, and a compact
log.

Each row shows title, source, installed/update state, and Workshop id when
available. Workshop rows may be installed or updated by the sync button and any
installed row may be deleted through a confirmation dialog. The screen does not
show enable, disable, or reorder controls because those settings belong to each
campaign and are changed inside the game.

## Failure and Safety Rules

- No game, DLC, Workshop payload, or third-party artwork enters the repository
  or APK.
- Workshop data is written only below app-private staging and
  `files/game/mods`.
- A download failure never replaces a working installed mod.
- Directory promotion rejects symlinks and content outside the staging root.
- The existing downloader supplies manifest and chunk validation; promotion
  separately requires a recognizable DD1 mod root.
- Delete is explicit and confirmed. It removes only the resolved direct child
  of `files/game/mods`, never a path supplied by Workshop metadata.
- No mod executable or script is launched by the manager.

## Testing

Host-side unit tests cover Workshop marker parsing, direct-child scans, payload
root selection, atomic promotion/rollback, delete-path confinement, paging
conversion, and sync-plan classification. Existing `ModList` and `ModSyncPlan`
tests remain green.

Waydroid instrumentation tests cover drawer navigation, empty/loading/error
states, populated Workshop/local rows, delete confirmation, and rotation. A
test fixture exercises staging promotion with fake payload bytes; it does not
need Steam or run the ARM64 game runtime.

Phone verification follows only after the Waydroid and Gradle suites pass. It
checks a real subscribed item download, `project.xml` recognition, update
detection through `time_updated`, game discovery of the installed mod, and
preservation of the old copy after a deliberately interrupted update.

## Deferred Work

- Editing each campaign's enabled set and load order requires DSON-aware save
  mutation, a snapshot before every write, and separate phone evidence.
- Workshop URL import and required-item expansion are added when subscription
  sync is proven on a real account.
- Conflict preferences between manually copied and Workshop copies are added
  if an actual same-id collision appears; direct directory identity already
  makes the collision visible and prevents silent overwrites.
