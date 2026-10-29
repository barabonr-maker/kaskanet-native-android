# KaskaNet Native Android (development build)

This is a native Android client written in Kotlin + Jetpack Compose. It is not a WebView wrapper. It communicates with the existing KaskaNet Node.js API using HTTPS and `Authorization: Bearer <token>`.

## Included in this starter

- Native login and registration screens (`/api/login`, `/api/register`)
- Session token persistence on device
- Native conversation list (`/api/convs`)
- Native text chat (`/api/msgs`, `/api/send`) with periodic refresh
- Native feed list and text post creation (`/api/feed`)
- Post likes (`/api/feed/like`)
- Profile read and logout (`/api/me`, `/api/logout`)
- Music search via the server's SoundCloud integration (`/api/music/search`); tapping a result opens its SoundCloud URL
- GitHub Actions workflow to build and upload a debug APK

## Backend

Default API base URL is configured in `app/src/main/java/net/kaskanet/app/MainActivity.kt`:

`https://kaskaneeet.duckdns.org`

Confirm that this is the exact public domain of your deployed server before release. The client depends on the existing API routes in `server/server.js`. It does not include or replace the server/database.

## Build an APK in GitHub Actions (no Android Studio required on your PC)

1. Create/open a GitHub repository you control.
2. Upload the **contents** of this `kaska-native` directory to the repository root (the `app` folder and `.github` folder should be at repository root).
3. Open the repository's **Actions** tab and enable Actions if asked.
4. Choose **KaskaNet Android build** and press **Run workflow** (or push a commit to `main`/`master`).
5. When the run succeeds, open the run and download the `KaskaNet-debug-apk` artifact. Extract the APK from the downloaded artifact ZIP and install it on an Android test device.

The workflow produces a debug APK for testing. It does **not** produce a signed Play Store release. A release AAB needs a release signing key stored as GitHub Actions secrets and additional release configuration; never commit a keystore or passwords to the repository.

## Current limitations — not store-ready yet

This is the first native implementation slice, not a complete feature-for-feature port of the web app. Media uploads, voice recording/playback, push notifications, real-time WebSocket updates, call UI/WebRTC, profile editing/photo selection, comments UI, group/channel creation and moderation, and comprehensive error/empty/loading states still need implementation and testing. Chat refresh currently polls the API every five seconds. The server's existing API and production domain must be tested before release.

## Tech stack

- Kotlin
- Jetpack Compose / Material 3
- OkHttp
- Coroutines
- Android SDK 35, minimum Android 8.0 (API 26)
