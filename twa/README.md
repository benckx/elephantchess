# Android app (Trusted Web Activity) — Approach A

This directory holds the configuration to build the ElephantChess Android app as a
**Trusted Web Activity (TWA)**: a thin native wrapper that renders https://elephantchess.io
full-screen (no browser URL bar) through Chrome. It targets Google Play.

The web side is already PWA-ready (served by the webapp module):

- `GET /manifest.webmanifest` — web app manifest
- `GET /sw.js` — service worker (scope `/`)
- `GET /images/pwa/*` — app icons (192/512 "any" + maskable, plus apple-touch-icon)
- `GET /.well-known/assetlinks.json` — Digital Asset Links (see below)

## Prerequisites

- Node.js 18+
- JDK 17+ (Bubblewrap downloads its own Android SDK/build tools on first run)
- `npm i -g @bubblewrap/cli`

## 1. Generate / update the Android project

`twa-manifest.json` is the Bubblewrap source of truth. Regenerate the Android project from it:

```bash
cd twa
bubblewrap build        # reads twa-manifest.json, produces app-release-signed.aab + .apk
```

First time only, if you prefer Bubblewrap to re-derive everything from the live manifest:

```bash
bubblewrap init --manifest https://elephantchess.io/manifest.webmanifest
```

On first `build`, Bubblewrap creates a signing keystore (`android.keystore`, alias `android`).
**Keep this keystore and its passwords safe and private — it is required for every future update
and must never be committed.** It is already covered by the repo `.gitignore` (`*.keystore`).

## 2. Wire up Digital Asset Links (removes the URL bar)

The site must assert it owns the app. Get the signing-certificate SHA-256 fingerprint(s):

```bash
# upload key (from your keystore)
keytool -list -v -keystore android.keystore -alias android | grep SHA256
```

Also copy the **Play App Signing** SHA-256 from the Play Console
(Release → Setup → App signing) once the app is uploaded.

Then set these environment variables on the webapp deployment (the
`/.well-known/assetlinks.json` route reads them):

```bash
ANDROID_TWA_PACKAGE_NAME=io.elephantchess.twa
ANDROID_TWA_SHA256_FINGERPRINTS=<upload-sha256>,<play-signing-sha256>
```

Verify: `curl https://elephantchess.io/.well-known/assetlinks.json` should list the package
and fingerprint(s). Without a valid match, the TWA shows a Chrome address bar.

## 3. Publish to Google Play

1. Create a Google Play Developer account ($25 one-time).
2. Upload `app-release-signed.aab`.
3. Complete the store listing (icon 512×512 — use `images/pwa/icon-512.png`, feature graphic,
   screenshots), content rating, data-safety form, privacy policy URL.
4. Roll out through internal → closed → production tracks.

## Updating the app later

Bump `appVersionCode` (and usually `appVersionName`) in `twa-manifest.json`, run
`bubblewrap build`, and upload the new `.aab` — signed with the **same** keystore from step 1.
