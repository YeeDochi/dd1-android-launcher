# Adding and updating one DLC at a time

Ticking a DLC on the content screen records a choice that only a whole new
download acts on: four gigabytes to add three hundred megabytes. Removing is
already immediate. This is the other half - fetching a single DLC into a game
that is already installed, and noticing when one that is installed has been
updated.

## What the launcher already does

`DD1InstallService.download()` hands one `AppItem` for app 262060 to the
downloader, which lays the whole game out in `files/staging/game`. When it
finishes, `DlcInstallFilter` removes the DLC that was not chosen and
`DD1Installer.activate` renames the tree over `files/game`. The install is
therefore always whole-tree: there is no way in to a single folder.

`DD1Installer.beginDownload` discards a staging tree left by an interrupted
attempt, because the downloader accepts right-sized files it finds on disk
without reading them, and `activate` refuses a tree whose launch executable is
missing or is not a real PE file. Both exist because a 3.7 GB install of
zero-filled files once reached the game.

## What was measured

Read from app 262060's own PICS entry on 2026-08-20, which the launcher can
already request because it asks Steam for package information at sign-in:

- `depots.<id>.dlcappid` names the DLC a depot belongs to. This is the mapping,
  not a guess from the numbering.
- `depots.<id>.config.oslist` is `windows`, `macos` or `linux`. Every DLC has
  exactly three depots, one per platform, and **the order is not fixed**: The
  Fire's Edge is windows, linux, macos while the others are windows, macos,
  linux. A depot chosen by offset from the DLC's app id fetches macOS content.
- `depots.<id>.manifests.public.gid` is the depot's current version.

So the depot to fetch for a DLC is the one whose `dlcappid` is that DLC and
whose `oslist` is `windows`, and its `gid` says which version that is.

DLC content is self-contained under `game/dlc/<appid>_<title>/`. The evidence is
that `DlcInstallFilter` deletes exactly those folders, and the 3.2 GB install
built that way on 2026-08-20 - with Butcher's Circus filtered out - runs.

## Design

### Reading the catalogue

The sign-in sweep already calls `picsGetProductInfo` for every licensed package.
It gains a second request for app 262060, and the depots it returns are reduced
to one row per DLC: app id, depot id, manifest gid.

The reduction is the part worth testing, so it takes plain rows rather than the
library's `KeyValue`: `DD1DepotCatalog.windowsDepots(List<DepotRow>)` returns a
map from DLC app id to `(depotId, manifestGid)`, keeping only `oslist=windows`
and ignoring rows without a `dlcappid`. The session does the `KeyValue` walking
and hands rows in.

### Recording what is installed

`files/dlc-versions` holds one `<appid>=<gid>` line per installed DLC. It sits
outside `files/game` so a tree replaced wholesale does not take the record with
it. Whoever changes what is on disk updates it in the same breath: a merge adds
the DLC it landed, a whole-tree install rewrites the file for the DLC it kept,
and the content screen's removal drops the lines it deleted. `DD1Installer`
neither knows nor guesses the selection, so the caller that has it passes the
gids in.

An installed DLC is out of date when its recorded gid differs from the
catalogue's. A DLC installed before this file existed has no line, which reads
as "version unknown" and is offered as an update rather than claimed current.

### Fetching

`DD1InstallService.downloadDlc(Set<Integer> appIds)` builds one `AppItem` with
an explicit depot list - the windows depot of each requested DLC - and the same
staging directory, marker and stall timeout as a full download. It is the same
listener, so the part count, the notification and the log all work unchanged.

The first thing to verify in implementation is that an explicit depot list is
honoured and still lays content out under `dlc/`. Everything below depends on
it, and it is one download on Waydroid to find out.

### Merging

`DD1Installer.merge(File filesDir, Collection<Integer> appIds)` replaces
`DD1Installer.activate` for this path, because a staging tree holding one DLC has
no launch executable and `activate` would rightly refuse it.

For each requested DLC, in order:

1. Find `staging/game/dlc/<appid>_*`. Absent means the download did not deliver
   it; the merge fails and says which.
2. Check its content, not its size. The folder must hold at least one file whose
   first bytes are not all zero. This is the same check that the whole-tree path
   makes on the executable, for the same reason.
3. Delete `game/dlc/<same folder>` if it is there and rename the staged folder
   over it.
4. Write the DLC's gid to `files/dlc-versions`.

Each DLC lands whole or not at all, and one failing does not undo the ones
before it - they are separate content. The staging tree and its marker are
cleared only when every requested DLC has landed, so a failure leaves the next
attempt to start clean rather than resume a tree the downloader would trust.

Merging writes into the installed game, so it refuses to run while the game is
running.

### The screen

Each row on the content screen reads as one of: installed and current,
installed with an update, not installed, or not selected. The existing
`선택 적용` button keeps removing what is unticked. A second action fetches the
ticked DLC that is missing or out of date, naming the total before it starts.
Nothing downloads on its own.

## Testing

Unit tests, which is where the logic that can be wrong quietly lives:

- `DD1DepotCatalog`: picks the windows depot, ignores the other two whatever
  their order, ignores depots with no `dlcappid`, and survives a DLC with no
  windows depot at all.
- `files/dlc-versions`: written, read back, a line dropped on removal, a missing
  file read as "nothing known".
- What needs fetching: given a catalogue, a record and a selection, produce the
  missing and the out-of-date.
- `DD1Installer.merge`: a zero-filled folder is refused, an absent folder is
  refused, an existing folder is replaced, and a failure part-way leaves the
  DLC that already landed in place.

On Waydroid, which can download but cannot run the game: the depot list is
honoured, a single DLC lands in the installed tree, and the game folder holds
real bytes afterwards. On the phone: the game starts and the DLC's content is
present in play.

## Not in this design

- Repairing the base game depot by depot. The same machinery would do it, but
  deciding what is damaged is its own problem.
- Automatic updates. Detection is automatic; fetching is a press.
- Language and macOS depots. The launcher installs the windows English build.
