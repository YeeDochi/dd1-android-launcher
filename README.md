# DD1 Android Launcher

An Android launcher for running a user-owned Windows copy of Darkest Dungeon
through Wine and Box64. It is based on Winlator 11.1 and does not contain game,
DLC, Workshop, or Steam account data.

This project is not affiliated with Red Hook Studios or Valve Corporation.

## Current development flow

1. Put an owned game installation in the app's `files/game` directory.
2. Open the DD1 home screen and create the runtime profile when prompted.
3. Press `Play`. The launcher mounts the game directory and starts
   `__build/x64_Debug/Darkest.exe` when present.

The DD1 home screen owns the normal launch flow. Winlator's container, input, and
runtime settings remain available as advanced screens.

The manual file placement is temporary. A legal import/download flow will replace
it before a public release.

## Build

Android SDK 34, NDK `24.0.8215888`, and CMake 3.22.1 are required.

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Testing

Waydroid is used for UI and profile-flow checks. Its x86_64 native bridge cannot
validate Winlator's nested ARM64 root filesystem execution. Game runtime testing
therefore requires an ARM64 Android device.

## Upstream and licenses

The exact Winlator revisions and third-party attribution are recorded in
[`NOTICE`](NOTICE). The source remains under the upstream LGPL-2.1 terms in
[`LICENSE`](LICENSE); bundled components retain their own licenses.
