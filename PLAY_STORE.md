# Publishing Net Speed Test on Google Play

## What's ready
| Item | File |
|---|---|
| App Bundle to upload (targets Android 16 / API 36, signed with your upload key) | `dist/NetSpeedTest-1.0.0.aab` |
| High-res icon, 512 × 512 | `docs/play-store/icon-512.png` |
| Feature graphic, 1024 × 500 | `docs/play-store/feature-graphic-1024x500.png` |
| Phone screenshots, 1080 × 1920 | `docs/play-store/phone-*.png` |
| Tablet screenshot, 1280 × 800 | `docs/play-store/tablet-landscape.png` |
| Privacy policy text | `PRIVACY.md` (host it at a public URL; see below) |

## Before your first upload
1. **Package name.** The app ID is `com.netspeedtest`. Play IDs are permanent and must be unique. If Play Console says it's taken, change `applicationId` in `app/build.gradle.kts` and `appPackage` in `offline-build/build.gradle.kts`, then rebuild. A form like `com.yourname.netspeedtest` is safest.
2. **Upload key.** The bundle is signed with the upload key in `upload.keystore`, with its passwords in `upload.properties`. **Back both up somewhere safe.** Every future update must be signed with this key. If it's ever lost, Play support can reset an upload key, but this takes days. Enrol in **Play App Signing** (the default): Google holds the final app-signing key.
3. **Privacy policy URL.** Publish `PRIVACY.md` somewhere public, for example GitHub Pages or a Google Site, and fill in your support email.
4. **Version.** Raise `versionCode` in both build files for every new upload (1, 2, 3, …).

## Store listing (suggested)
- **App name:** Net Speed Test
- **Short description (80 chars):** Fast, accurate internet speed test. No ads, no tracking, no account.
- **Full description:**
  > Test your internet in seconds. Net Speed Test measures download, upload, ping, jitter and latency under load, using Cloudflare's global network, with the nearest of 300+ data centres worldwide.
  >
  > • Live gauge and speed graph while testing
  > • Your provider, IP address and test server
  > • Multi or single connection mode
  > • What your connection is good for: 4K streaming, gaming, video calls
  > • Wi‑Fi and mobile details: band, link speed, 4G/5G, signal
  > • Battery, charging current, temperature and memory at a glance
  > • Test history saved on your phone. Share any result.
  > • Dark and light themes
  >
  > Private by design: no ads, no analytics, no account. Tests only run when you tap Start and stop when you leave the app.
- **Category:** Tools
- **Tags:** Speed test, Internet, Network, Wi‑Fi

## Play Console questionnaires
- **Ads:** No, the app contains no ads.
- **App access:** All functionality is available without restrictions (no login).
- **Content rating:** Utility / tools app with no user-generated content, violence, gambling or sharing of location. Expected result: *Everyone / PEGI 3*.
- **Target audience:** 13 and over. The app is not designed for children, which keeps it out of the Families programme requirements.
- **News app:** No. **Government app:** No. **Financial features:** None. **Health:** None.
- **Data safety (suggested answers):**
  - *Does your app collect or share any of the required user data types?* **No.** The developer receives no data. Speed-test traffic goes directly to Cloudflare as part of the network connection, and results stay on the device.
  - *Is all user data encrypted in transit?* **Yes** (HTTPS only).
  - *Can users request that data be deleted?* Data never leaves the device. Users can delete history in the app, and uninstalling removes all data.
  - You are responsible for these declarations. Review them against Google's current Data safety guidance before submitting.

## Worldwide behaviour
- **Servers.** Cloudflare's anycast network answers from the data centre nearest the user (300+ cities in 120+ countries), over IPv4 and IPv6.
- **Busy networks.** On carrier-grade NAT, where many users share one IP, rate-limited requests (HTTP 429/503) are retried with backoff. Only a sustained rate limit shows "Server is busy".
- **Data caps.** Mobile and metered connections use smaller test sizes: at most about 150 MB down and 60 MB up per test. Wi‑Fi allows larger transfers for accuracy on fast links.
- **Slow and unstable links.** Request sizes adapt to the measured speed. Stalls, timeouts, captive portals (hotel/airport Wi‑Fi), airplane mode, network switches and going offline each show a clear message.
- **Screens.** Phones, tablets, foldables and landscape (content width is capped on large screens), right-to-left locales, and system font scaling up to at least 1.6×.
- **Language.** The interface is currently **English only**. Numbers and dates follow the phone's locale.

## Known limitations to be aware of
- Speed tests rely on Cloudflare's public speed-test endpoints. They are free and widely used, but there's no service agreement. If Cloudflare is blocked in a country or network, tests there fail with "Test server unavailable". A second provider can be added in `SpeedTestServer.kt`.
- The app has not yet been run on physical devices. Before publishing, install the bundle through a Play **internal testing** track and try it on a few phones: Wi‑Fi, mobile data and at least one tablet.
