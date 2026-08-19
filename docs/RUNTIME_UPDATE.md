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
is upstream Winlator and carries no local edits.

## Procedure

1. Check out the new upstream release next to this repository.
2. Replace every path listed as upstream above with the release's version.
3. Restore `app/AndroidManifest.xml` and `app/build.gradle` from this repository
   and re-apply upstream's own changes to them by hand; both files are small.
4. Build, run the unit tests, then launch the game once on a device.

## Rule

Do not edit upstream files. If the launcher needs behaviour the runtime does not
offer, add it in `com.winlator.dd1` and use the runtime's existing entry points:
`XServerDisplayActivity` accepts `container_id` and `exec_path` intent extras, and
containers carry per-game settings.
