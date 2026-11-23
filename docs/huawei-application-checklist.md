# Huawei Health Kit application checklist

Follow these steps in order, in a browser, signed in to your verified HUAWEI Developers account.
Each step says exactly what to enter. Do not skip the "check" steps.

## Values to use

| Field | Value |
|---|---|
| App name | your-health-sync |
| Package name | `io.github.wallwallfacer.yourhealthsync` |
| Platform | Android |
| Release key SHA-256 | `11:8A:8B:28:93:33:C2:52:36:1D:6C:15:63:7C:0D:45:2E:8C:06:43:D2:06:64:59:16:AB:C7:67:1C:6D:E9:D4` |
| Debug key SHA-256 | `85:F2:3F:A8:83:EA:83:03:36:6F:FF:45:07:73:52:CC:87:FF:4F:40:DF:71:80:6B:9D:1C:46:72:0B:04:B3:0C` |

The release key lives at `~/.android/yourhealthsync-release.jks` (alias `yourhealthsync`).
Its password is in the macOS Keychain: `security find-generic-password -s yourhealthsync-release-keystore -w`.
**Back this file up.** Losing it means a new fingerprint and a new Huawei registration.

## Step 1: Create the app

1. Open AppGallery Connect: https://developer.huawei.com/consumer/en/service/josp/agc/index.html
2. Go to **My projects** → **Add project**. Name it `your-health-sync`.
3. Inside the project, click **Add app**. Choose **Android**, enter the app name and package name from the table above.
   (The app does not need to be published in AppGallery.)
4. Open **Project settings** → **General information** → **App information**.
   Add **both** SHA-256 fingerprints from the table above.
5. Write down the **App ID** shown on that page. You will put it into `local.properties` later as `huawei.appId=<App ID>`.
6. Do **not** commit `agconnect-services.json` if you download it.

## Step 2: Apply for Health Service Kit (Health Kit)

1. Open HUAWEI Developers console → **Development** → **Health Service Kit** (it may be listed as "Health Kit") → **Apply**.
2. Select the app created in Step 1.
3. Request **read** access only, for these data scopes:
   - **Height and weight** (read)
   - **Body fat** / body composition (read). It covers body fat rate, BMI, muscle, water, bone, protein and skeletal muscle.
   - **Historical data** (read data from before the authorization date), if the form offers it. Pick the longest range offered.
4. **Check:** the scope names in the form may differ from the words above. Pick the ones whose descriptions match.
   Do not request write scopes or other data types (steps, sleep, heart rate).
5. Purpose / description text you can paste:
   > Personal, open-source app that copies the user's own weight and body-composition measurements from HUAWEI Health to Android Health Connect on the same phone, so they appear in other health apps. Data never leaves the device; there is no server.
6. Privacy statement URL: `https://wallwallfacer.github.io/your-health-sync/PRIVACY` (published later; if the form requires it now, tell Claude so it can be published first).
7. Submit.

## Step 3: Check the result

1. Right after submitting, open the Health Service Kit page for the app again.
2. Note the status of the scopes: are **test scopes** already active (usable by up to 100 test users), or is everything pending review?
3. If there is a **test user** list, add your own HUAWEI ID.
4. Report the status back (active test scopes / pending / rejected, and any message from Huawei).

## Step 4: Prepare the phone (can be done any time)

1. On the Samsung phone, install **AppGallery** (https://appgallery.huawei.com), then **HMS Core** from AppGallery.
2. Install / update **HUAWEI Health** and sign in with the same HUAWEI ID.
3. In HUAWEI Health: **Me** → **Privacy management** (or **Settings** → **Data sharing and authorization**) → turn on data sharing for Health Kit.
4. Enable USB debugging on the phone (Settings → About phone → Software information → tap Build number 7 times; then Developer options → USB debugging) and connect it to the Mac.
