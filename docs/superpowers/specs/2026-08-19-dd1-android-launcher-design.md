# DD1 Android Launcher Design

## Purpose

Build a GPL-3.0-or-later Android launcher dedicated to a user-owned Steam copy of Darkest Dungeon 1. The launcher authenticates directly with Steam, downloads only content the signed-in account owns, runs the official Windows build through the embedded Winlator runtime, and synchronizes saves with the PC version through Steam Cloud.

The APK and source repository never contain Darkest Dungeon executables, assets, DLC, Steam credentials, or decrypted Steam content. The user experience remains single-game: sign in, install, play.

## Supported systems

- Minimum target: Android 11, ARM64, 6 GB RAM, Vulkan 1.1, Snapdragon 865-class performance.
- Primary development phone: Galaxy S25.
- UI and installer test environment: Waydroid x86_64 inside a visible Gamescope window.
- Product runtime: Winlator 11.1 components embedded in the same APK.
- Waydroid verifies UI, authentication, download, installation, and profile flow. Native ARM64 phones verify actual Wine/Box64 gameplay.

## Legal and security boundary

- Verify ownership of Steam App ID `262060` before offering installation.
- Download the current public Windows depots and every DLC depot owned by the account. Never synthesize DLC ownership or modify DLC checks.
- Send authentication data only to Steam endpoints.
- Prefer Steam Mobile QR approval. Credential login is a fallback and passwords are never persisted.
- Encrypt the persistent Steam refresh token with an Android Keystore key. Sign-out deletes the token and key material.
- Store game, saves, logs, and download chunks only in app-private storage.
- Preserve upstream GPL and third-party notices. The project is not affiliated with Red Hook Studios or Valve.

## User-visible states

The home screen is driven by one persistent installer state:

1. `SIGNED_OUT`: no valid Steam session; show QR sign-in and credential fallback.
2. `AUTHENTICATING`: show QR approval or Steam Guard progress and a cancel action.
3. `NOT_OWNED`: explain that App ID `262060` is not owned; show sign-out only.
4. `READY_TO_INSTALL`: show owned base game and DLC summary plus Download.
5. `DOWNLOADING`: show overall bytes, current file, transfer rate, progress bar, Pause, and a scrollable detailed log.
6. `VERIFYING`: validate the staging tree and display the file being checked.
7. `READY`: show Play, Update/Repair, Saves, Logs, and Sign out.
8. `ERROR`: retain the failed operation log and show Retry and Export Logs.

If the game payload is missing, Play is never shown. If a valid payload already exists, Steam sign-in is optional until update, repair, or cloud sync is requested.

## Storage layout

```text
files/
  game/                 # validated Windows Steam payload
  staging/game/         # incomplete or newly downloaded payload
  saves/                # stable Wine save root shared with cloud sync
  steam/session/        # non-secret Steam metadata; token remains encrypted
  cache/steam/          # resumable depot chunks and manifests
  logs/install/         # authentication and DepotDownloader logs
  logs/runtime/         # Winlator and game session logs
  snapshots/saves/      # bounded pre-sync save snapshots
```

`game/` is replaceable. Updates never remove `saves/`, `logs/`, or save snapshots. A validated staging payload replaces `game/` with a same-filesystem rename. A failed installation leaves the last valid `game/` untouched.

## Steam authentication

Use JavaSteam `1.8.0` in the Android process.

- Begin with `beginAuthSessionViaQR` and render the returned challenge URL as a QR code.
- Poll until Steam Mobile approves, the challenge changes, the user cancels, or the session expires.
- Credential fallback accepts username and password in memory and presents Steam Guard challenges explicitly.
- Request a persistent session and store only the resulting refresh token through Android Keystore encryption.
- Reconnect with the refresh token on later launches. Authentication failures return to `SIGNED_OUT`; they are never retried silently.

After login, query package/app metadata and licenses. Continue only when the account owns App ID `262060` or an applicable package containing it.

## Game and DLC download

Use `javasteam-depotdownloader:1.8.0` with its Android installation directory set to `files/staging/game` and the platform filter fixed to Windows.

- Resolve the current public branch manifests for the base game and owned DLC.
- Show one Download action for the complete owned installation rather than exposing depot IDs.
- Run the transfer in an Android foreground service so it survives screen locking and activity recreation.
- Publish immutable progress snapshots to the home screen: phase, downloaded bytes, total bytes, current relative path, bytes per second, and the latest log lines.
- Append the complete timestamped stream to `logs/install/<session>.log`; retain the latest five install sessions.
- Keep at most 1,000 lines in the visible log view while the file log remains complete.
- Pause only at a file boundary. Retry resumes from verified files and cached chunks.
- Cancel preserves resumable cache but removes partial files that fail their expected checksum.

Validation requires a regular `__build/x64_Debug/Darkest.exe`; the `audio`, `campaign`, `dungeons`, `heroes`, and `shared` directories; and every selected depot manifest. Paths containing traversal segments or escaping symlinks are rejected. The first failed requirement is shown in the UI and written to the log.

## Runtime integration

The launcher keeps one internal Winlator profile and exposes no generic container, profile, or per-game setting menus.

- Mount `files/game` as drive `G:`.
- Launch `G:\__build\x64_Debug\Darkest.exe` through Wine and Box64.
- Use the launcher-selected graphics and audio defaults; device-specific fallback remains automatic.
- Map the Wine user save directory to `files/saves` so replacing the game payload cannot remove progress.
- Write each launch to a timestamped runtime log and retain the latest five sessions.

## PC save sharing

Use JavaSteam's Steam Cloud handler with App ID `262060`. Synchronization is explicit at safety boundaries:

1. Before Play, snapshot local saves and compare local and remote file summaries.
2. If only cloud changed, download cloud files into a staging save directory, validate them, and atomically apply them locally.
3. If only local changed, upload the changed non-empty save files and commit the batch.
4. If both changed, require `로컬 유지` or `클라우드 유지`; never choose automatically.
5. After the game exits, snapshot and upload only when local saves changed during that session.

Each summary contains relative path, byte length, modification time, and SHA-1. A recognizable save tree contains a `profile_<number>` directory with a non-empty `persist.game.json`. Reject absolute paths, traversal, empty replacement sets, files larger than Steam's 100 MiB per-file limit, and unrecognizable save trees. Keep the latest three local snapshots. A cloud error never deletes or overwrites the current local save tree.

Sign-out disables cloud operations but leaves local saves and the installed game intact.

## Error handling and diagnostics

- Authentication, download, validation, runtime, and cloud operations have distinct error states.
- Every long operation is cancellable and survives activity recreation.
- Network loss pauses download or cloud work without corrupting the active payload or saves.
- Export Logs creates a ZIP through Android's share sheet and excludes passwords, refresh tokens, cookies, and authorization headers.
- Steam authentication and destructive save choices are never retried or resolved silently.

## Testing strategy

- Pure JVM tests cover state transitions, ownership decisions, progress aggregation, path validation, payload validation, log redaction, save summaries, and conflict selection.
- Android tests cover QR-state restoration, foreground-service reconnection, Keystore token storage, download UI recreation, and log export.
- Waydroid in Gamescope verifies sign-in UI, owned-content display, download progress/logging, resume, validation, and installed-state transitions.
- Galaxy S25 verifies authentication, full Depot/DLC installation, Wine/Box64 launch, save creation, cloud upload/download, conflict handling, and sustained gameplay.
- A Snapdragon 865-class ARM64 device is required before declaring the minimum specification verified.

## Delivery order

1. Steam QR/Guard authentication, token storage, and ownership verification.
2. Foreground Depot/DLC download, visible logs, resume, validation, and atomic installation.
3. Runtime launch against the downloaded Windows payload.
4. Stable save mapping, snapshots, and bidirectional Steam Cloud synchronization.
5. Workshop browser and mod enable/disable/load order.

## Deliberate exclusions

- No game or DLC data in the APK or repository.
- No SteamCMD, external Steam app, backend account service, or generic multi-game UI.
- No automatic cloud conflict resolution.
- No editable performance profiles until measured device compatibility requires one.
- No Workshop support before installation and cloud sync are reliable.
- No Butcher's Circus support promise because its Steam networking behavior remains unverified.
