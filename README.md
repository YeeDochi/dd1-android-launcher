# DD1 Android Launcher

An Android launcher for running a user-owned Windows copy of Darkest Dungeon
through Wine and Box64. It is based on Winlator 11.1 and does not contain game,
DLC, Workshop, or Steam account data.

This project is not affiliated with Red Hook Studios or Valve Corporation.

## Current flow

1. Open the DD1 home screen. If no valid game installation exists, sign in to
   Steam by QR code or account and password. Passwords are neither stored nor
   logged; accounts using the password path must approve the request in Steam
   Mobile.
2. After the launcher verifies ownership of app `262060`, press `Download`.
   The owned Windows game and DLC are downloaded into app-private staging while
   byte, file, and sanitized log progress remains visible.
3. A validated staging tree atomically replaces `files/game`. Interrupted or
   invalid downloads never replace the active installation.
4. The launcher creates its single internal runtime profile automatically.
   Press `Play` to mount the game directory and start
   `_windows/win64/Darkest.exe` or the compatible no-Steam executable when
   present.

The DD1 home screen owns the launch flow. Winlator's container and runtime
settings are internal implementation details and are not exposed to users.

Steam refresh tokens are encrypted with Android Keystore. The APK and repository
contain no game, DLC, Workshop, save, or Steam account payload. Steam Cloud save
sync and Workshop installation are planned follow-up features.

## Build

Android SDK 34, NDK `24.0.8215888`, and CMake 3.22.1 are required.

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Testing

Waydroid hosted in a visible Gamescope window is used for login, installer UI,
service lifecycle, and profile-flow checks. Its x86_64 Houdini/native-bridge path
does not prove Winlator's nested ARM64 gameplay execution. Final game runtime and
performance testing therefore requires an ARM64 Android device.

## Upstream and licenses

The exact Winlator revisions and third-party attribution are recorded in
[`NOTICE`](NOTICE). The source remains under the upstream LGPL-2.1 terms in
[`LICENSE`](LICENSE); bundled components retain their own licenses.
