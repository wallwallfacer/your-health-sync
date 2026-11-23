# Your Health Sync

Open-source Android app that copies your **weight and body composition** from **HUAWEI Health** to **Android Health Connect**, which **Samsung Health** (and other apps) read from. It's a free alternative to the paid Health Sync app, covering this one job.

```text
HUAWEI scale ─► HUAWEI Health ─(Health Kit)─► Your Health Sync ─► Health Connect ─► Samsung Health
```

| HUAWEI data | Health Connect | Shown in Samsung Health |
|---|---|---|
| Weight | Weight | yes |
| Body fat rate | Body fat | yes |
| Basal metabolism | Basal metabolic rate | yes |
| Bone salt | Bone mass | no (Health Connect only) |
| Moisture | Body water mass | no (Health Connect only) |

BMI, visceral fat, muscle mass and similar values are not copied, because Health Connect has no type for them.

## How it syncs
- Every 6 hours (and when you tap **Sync now**) it re-reads the last 30 days and upserts them. Re-reading never creates duplicates.
- **Backfill last year** (in the ⋮ menu) imports up to one year back (HUAWEI Health Kit's history limit).
- Each data type can be switched off on the home screen; switched-off types are not written.
- One way only: nothing is written back to HUAWEI. Deleting a weigh-in in HUAWEI Health does not delete it in Health Connect.

## Install (Samsung or any Android phone)
1. Install **AppGallery**, then **HMS Core** from AppGallery, then **HUAWEI Health**, and sign in.
2. Download the APK from [Releases](https://github.com/wallwallfacer/your-health-sync/releases) and install it.
3. Open the app and follow the three setup steps: **source** (tap **Connect HUAWEI Health**), **destination** (tap **Allow access** for Health Connect) and **data types**. Then tap **Sync now** on the home screen, which shows the latest synced value per data type.
4. In Samsung Health: **Settings → Health Connect → App permissions**. Allow Samsung Health to read body composition data.

> HUAWEI approves Health Kit access for one specific signed app. Only the official release APK can read HUAWEI data. A build you make yourself cannot, until you register your own app (see below).

## Build it yourself
Requirements: JDK 17 and the Android SDK (platform 37.2).

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew testDebugUnitTest assembleDebug
```

To read HUAWEI data with your own build:
1. Register an app in AppGallery Connect with your own package name, and add your signing key's SHA-256.
2. Apply for HUAWEI Health Kit with the scopes *height & weight (read)*, *body fat (read)* and *historical data (1 year)*. See [docs/huawei-application-checklist.md](docs/huawei-application-checklist.md).
3. Put your App ID in `local.properties`: `huawei.appId=<your App ID>`.

Signed release build:
```bash
YHS_KEYSTORE=~/.android/yourhealthsync-release.jks \
YHS_KEYSTORE_PASSWORD="$(security find-generic-password -s yourhealthsync-release-keystore -w)" \
./gradlew assembleRelease
```

Debug builds also include an **HC spike** launcher entry. It writes test values to Health Connect so you can check what Samsung Health shows, without HUAWEI access.

## Privacy
No server, no analytics. See [PRIVACY.md](PRIVACY.md).

## License
MIT
