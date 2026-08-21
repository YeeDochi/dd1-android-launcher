# DD1 Mod Hub Design

**Date:** 2026-08-21

## Goal

Replace the diagnostic-looking Workshop synchronizer with a native Android mod
manager that combines Steam Workshop discovery with installed-mod management.
The screen should feel like a small storefront: visual cards, search, useful
sorting, clear ownership-like states, and direct actions.

The design takes interaction ideas from the MIT-licensed
`iunius612/StS2-Launcher_Mod_Manager` Mod Hub without copying its Godot/C# UI.
Its attribution must be added to `THIRD_PARTY_NOTICES` when implementation lands.

## Scope

The first complete Mod Hub provides:

- a **Workshop** tab for global Darkest Dungeon Workshop discovery;
- search by words and direct lookup by Workshop URL or published-file ID;
- Popular, Newest, Recently Updated, and Top Rated sorting;
- preview images cached in `cacheDir` and loaded without blocking the UI thread;
- item cards with title, author or publisher metadata when available, subscriber
  count, rating, file size, state, and one primary action;
- an **Installed** tab combining subscribed, installed, disabled, orphaned, and
  manually copied mods;
- subscribe, unsubscribe, install/update, enable, disable, and delete actions;
- per-item download state and progress in the card instead of a log panel;
- retryable inline errors and empty states.

Campaign-specific enable sets and load order remain in Darkest Dungeon. The
launcher only offers a global stash: disabling a mod moves its directory out of
the game-visible `game/mods` tree, and enabling it moves it back. The launcher
does not mutate campaign DSON data in this phase.

## Deliberate Omissions

- No embedded Steam WebView. It would introduce a second login/cookie session
  and would not share the launcher's JavaSteam authentication reliably.
- No comments, discussions, change-note reader, dependency graph, tag browser,
  or infinite scroll in the first screen. Add these after real-account search,
  subscription, and download are proven on the phone.
- No automatic update while the game is running. All installs remain explicit.
- No bundled Workshop or game artwork. Every preview is fetched at runtime into
  cache storage only.

## Screen Structure

The existing drawer entry continues to open `DD1WorkshopFragment`, but the
visible title becomes **Mod manager**.

The header contains Back, title, and Refresh. Beneath it are two tabs:

1. **Workshop**
   - Search field and Search button.
   - A compact sort selector.
   - A two-column card grid in landscape; one column when width is constrained.
   - Initial load uses Popular and the empty search string.
   - A final “Load more” card requests the next page.
2. **Installed**
   - One scrollable list for Workshop and local mods.
   - Filters are unnecessary initially because typical DD1 mod counts are small.
   - Cards expose only actions valid for their current state.

There is no permanent log pane. During a download, the relevant card shows a
progress bar and short status. Global failures appear as an inline retry state;
action failures remain on the affected card.

## Workshop Data

`DD1SteamSession` uses JavaSteam's `PublishedFile.QueryFiles` service for global
discovery. Requests are scoped to app `262060`, return full details and vote
data, use 20 results per page, and include optional search text. Query sort maps
to Steam's published-file query types.

The existing subscribed-file query remains authoritative for subscription
state. Search results are joined with subscriptions and the local scan by
published-file ID. `PublishedFile.Subscribe` and `Unsubscribe` mutate the Steam
account only after the user presses the corresponding card action. A successful
subscription refreshes state and queues that item for download. Unsubscription
requires confirmation and removes the local copy only after Steam confirms it.

A presentation model carries only the fields the UI needs:

- published-file ID;
- title and short description;
- preview URL;
- file size, subscriptions, and vote score;
- updated timestamp and downloadable flag;
- subscribed, installed, disabled, update-available, and busy state.

Search generations are numbered. A late response from an older search is
discarded rather than replacing a newer query.

## Image Cache

`DD1WorkshopImages` reuses the repository's existing `HttpURLConnection` and
`BitmapFactory` pattern. Cache filenames are the SHA-256 hash of the preview URL
under `cacheDir/dd1-workshop-images`. Downloads use bounded connect/read
timeouts, a maximum response size, a temporary file, and rename on completion.
HTTP redirects are accepted only to HTTP(S). Failures return no bitmap; cards
retain a neutral placeholder and remain usable.

The UI owns request cancellation by generation. No image-loading dependency is
added.

## Local Mod State and Safety

Enabled mods remain direct children of `files/game/mods`. Disabled mods are
direct children of `files/game/mods-disabled`. Both roots are scanned. A mod's
identity is its top-level directory and, for Workshop items, its existing marker.

Enable and disable are same-filesystem renames. Before moving, both source and
destination are canonicalized and must be direct children of their expected
roots. The destination must not exist. No overwrite or merge is allowed.

Workshop updates download into the existing staging area. A disabled Workshop
item is updated in the disabled root so updating it does not silently enable it.
Deletion uses the existing confirmed, confined recursive delete. Unsubscribing
and deleting are distinct until Steam confirms the unsubscribe operation.

## Service and UI State

`DD1InstallService` remains the sole owner of the Steam session and download
worker. It publishes immutable Mod Hub snapshots containing:

- selected tab/query/sort and paging state;
- Workshop result cards;
- installed cards;
- loading, paging, and per-item action state;
- one global error, if any.

The service serializes mutations through its existing single worker. Search and
image fetches may run concurrently, but they never mutate files. A game download
and a Workshop payload download continue to share the single downloader guard.

Fragment recreation observes the latest snapshot and reconstructs the same
screen. UI callbacks verify the fragment is still attached before touching
views.

## Error Handling

- Signed out: show the storefront shell plus a concise Steam-login requirement.
- Search failure: preserve previous cards and show Retry.
- Thumbnail failure: show the placeholder only.
- Subscribe failure: restore the original button and show the reason on its card.
- Download failure: preserve the previous active or disabled copy and offer Retry.
- Enable/disable collision: leave both directories untouched and explain the
  conflicting name.
- Unsubscribe failure: keep both Steam subscription and local files unchanged.

## Testing

Plain JVM tests cover:

- query construction for every sort and search string;
- conversion of Steam details into card data;
- URL/ID parsing;
- joining search, subscription, active, disabled, and update states;
- confined enable/disable moves and collision handling;
- image-cache key and response-size checks.

Waydroid instrumentation covers:

- opening the Mod manager from the drawer;
- Workshop and Installed tab switching;
- two-column card rendering with a fixture image;
- search submission and sort selection without clipping at 1280x720;
- installed-card enable/disable action visibility;
- absence of the old log panel.

The ARM64 phone remains required for real Steam search, subscribe/unsubscribe,
Workshop payload layout, download interruption, update, and game discovery.
