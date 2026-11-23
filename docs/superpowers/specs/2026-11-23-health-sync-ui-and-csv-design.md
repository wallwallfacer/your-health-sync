# Design: Health Sync–style UI + CSV file source

Status: approved 2026-11-23. Extends [2026-11-23-huawei-to-health-connect-design.md](2026-11-23-huawei-to-health-connect-design.md).

> **Scope change (2026-11-23, during implementation):** the CSV source was dropped; only the UI redesign shipped, with HUAWEI Health as the single source. The wizard is source → destination → data types, and the dashboard has no "Import CSV" action. The CSV parser and import code from the first implementation commit is still in the tree but is not wired to any screen.

## Context
Today the app has one source (HUAWEI Health Kit) and one flat screen of numbered buttons. Health Kit needs Huawei's scope review (10–15 working days), so the app can't be used yet. The user wants:
1. **The Health Sync app's flow** (structure only, our own Material 3 visuals): pick a source, pick the destination, toggle data types, then a dashboard with one row per type.
2. **A CSV file as a second source**, so data can reach Health Connect now, without waiting for approval. Health Kit stays as an optional source.

User decisions (from Q&A): Health Sync flow (not a pixel copy) · fixed, documented CSV format · one-shot import through the system file picker · first-run wizard + dashboard · keep Health Kit (the "slow" part is the approval wait).

```text
                 ┌── HuaweiBodySource (Health Kit, windowed, periodic 6h) ──┐
 Source (choose) ┤                                                          ├─► enabled-metric filter ─► HealthConnectSink ─► Health Connect ─► Samsung Health
                 └── CsvParser (one-shot import of a picked file) ──────────┘          (SyncEngine)            (chunked insertRecords)
```

## Approaches (one sensible way)
- **Chosen:** a pure `CsvParser` (text → samples + rejected rows) and a new `SyncEngine.import(samples)` that filters by enabled metric and writes in chunks. The Huawei path keeps its windowed reads.
- Rejected: wrapping the CSV as a windowed `BodySource`. Windows exist only because of Huawei's query-span limit, and a file is already in memory.
- Rejected: adding Navigation Compose for the 3-step wizard. A sealed screen state in one activity is enough.

## Decisions I made (object if wrong)
- **Source-agnostic record ID, rounded to the minute:** `body:<metric>:<epochMillis truncated to minute>`, replacing `huawei:…`. A weigh-in that arrives from both a CSV import and Health Kit then upserts to one record instead of duplicating, as long as the two times agree to the minute. Two weigh-ins within the same minute merge, which is acceptable. This is safe to change now because no release or tag exists.
- **Record version for CSV imports = import time (epoch millis).** Health Connect may ignore an upsert whose `clientRecordVersion` equals the stored one (marked "likely" in the original spec; check the HC docs). A corrected CSV that is re-imported must overwrite, so every CSV import writes with a version above every earlier one. Huawei sync keeps `MAPPING_VERSION`. As a result, a value written by a CSV import is not overwritten by a later Huawei read of the same minute, which is acceptable.
- **CSV import is always available.** The wizard's source choice sets what the **periodic** sync reads. "Import CSV file" stays in the dashboard menu even when the source is HUAWEI, because Huawei backfill stops at one year and CSV is the route for older history. The import path builds its own `SyncEngine(sink only)` and never touches `Syncer.engine` or HMS, so it works without HMS installed.
- **CSV format (fixed):** UTF-8, comma-separated, header required.
  `time,weight_kg,body_fat_pct,bmr_kcal,bone_mass_kg,body_water_kg`
  - `time`: ISO-8601. Either with an offset (`2026-11-21T08:12:00+08:00`) or local (`2026-11-21T08:12` or `2026-11-21 08:12:00`), which is read in the device's zone.
  - Only `time` is required. The other columns are optional, and empty cells are skipped. Unknown columns are ignored, column order is free, and a BOM and CRLF line endings are tolerated. A decimal point is required.
  - A bad row (unparseable time or number, or a time before 2000 or more than one day in the future) is rejected with its line number. Other rows still import.
- **Per-type toggles:** stored in prefs. All five are on by default. Wizard step 2 requests all five permissions, and step 3 starts with the granted types ticked. After setup, turning a type on asks for its permission if it's missing. If Android shows no dialog (repeated denials), the app opens the Health Connect settings instead. and samples are filtered to **enabled ∩ granted** types before `insertRecords`. A partial grant therefore just skips the ungranted types instead of failing the whole call, and the dashboard marks those rows "not allowed".
- **Write chunking:** at most 1000 records per `insertRecords` call. **Unverified:** the documented Health Connect per-call limit. Check it during implementation and lower the chunk size if needed.
- **Worker:** scheduled only when source = HUAWEI and Health Connect is granted. Choosing CSV cancels the unique periodic work.
- **Import report:** "Imported N records · K rows rejected (lines …) · S values skipped as implausible · F failed (first error …)". One invalid record fails its whole chunk, so failures are counted per chunk. `RecordMapper` drops values out of range, so the engine counts those separately.
- **Per-type dashboard status:** a new `MetricStatus` (SharedPreferences) stores, per metric, the latest written value and its time, plus the last run time. It replaces `SyncStatus.describe`.
- **Huawei review requirements kept:** the privacy link sits on wizard step 1 and in the dashboard menu (within 4 taps). Revoke HUAWEI stays in the menu.

## UI (Compose, single `MainActivity`, sealed `Screen` state; settings in `AppSettings` SharedPreferences)
```text
Wizard (first run, or "Change source")          Dashboard
1 Source:  (•) CSV file  ( ) HUAWEI Health Kit   [Source ─► Health Connect]   ⋮ menu
           note: HUAWEI needs approved build     rows: [x] Weight 72.4 kg · 21 Nov 08:12
           Privacy statement link                      [x] Body fat 18.2 % · …
2 Destination: Health Connect [Allow access]     main button: CSV → "Import CSV file"
           (asks for all 5)                                   HUAWEI → "Sync now"
3 Data types: 5 checkboxes (granted ticked)      menu: Change source · Data types · Import CSV file ·
           → [Finish]                             Backfill (HUAWEI only) · Connect/Revoke HUAWEI · Privacy
```
- HUAWEI source: wizard step 1 → "Connect HUAWEI Health" (existing `HuaweiAuth`) before step 2.
- Toggling a type on the dashboard re-requests the Health Connect permission when it is missing.
- File picker: `ActivityResultContracts.OpenDocument` with `["text/csv","text/comma-separated-values","text/plain","application/csv","application/vnd.ms-excel","*/*"]`. Read the file through `contentResolver`, off the main thread.

## Files
New
- `app/src/main/java/.../csv/CsvParser.kt`: pure: `parse(lines, zone): CsvParseResult(samples, rejected: List<RejectedRow(line, reason)>)`.
- `app/src/main/java/.../settings/AppSettings.kt`: source enum (`HUAWEI`, `CSV`), enabled metrics, setupDone.
- `app/src/main/java/.../sync/MetricStatus.kt`: per-metric last value/time.
- `app/src/main/java/.../ui/WizardScreens.kt`, `ui/DashboardScreen.kt`: composables.
- `app/src/test/java/.../csv/CsvParserTest.kt`.
- `docs/sample-body.csv`: example file with **invented** values (public repo). `docs/superpowers/specs/2026-11-23-health-sync-ui-and-csv-design.md`: design doc.

Modified
- `sync/RecordMapper.kt`: new ID scheme. `RecordMapperTest` updated.
- `sync/SyncEngine.kt`: `metrics: () -> Set<Metric>` filter applied to both paths. `import(samples)` with write chunking. `SyncResult` gains `skipped` and `latest: Map<Metric, BodySample>`. `SyncEngineTest` extended (filtering, chunking, import).
- `hc/HealthConnectSink.kt`: `permissionsFor(metrics)`, `hasPermissions(context, metrics)`. Return the written and skipped counts.
- `sync/SyncWorker.kt`: source-aware schedule and cancel. `Syncer` reads `AppSettings`. `SyncStatus` replaced by `MetricStatus`.
- `ui/MainActivity.kt`: rewritten around the wizard and dashboard. `ui/PrivacyActivity.kt` text.
- `README.md`, `PRIVACY.md` (both languages), `TERMS.md`: mention the CSV source and the format. The README documents the CSV header and the Samsung step.
- Existing helpers reused: `HuaweiAuth` (connect/revoke/errorOf), `HuaweiBodySource`, `HuaweiFieldParser`, `Metric`, `BodySample`, the `BodySink` interface.

## Execution order
0. `git checkout -b feat/health-sync-ui-csv` (currently on `main`, clean). Write and commit the design doc.
1. **CSV slice first (TDD):** `CsvParserTest` then `CsvParser`. ID change plus `RecordMapperTest`. Engine `import`, filter and chunking, with tests. Temporary wiring for the device check: one "Import CSV" button on the current screen, which step 4 replaces. Commit.
2. **Device check of CSV → HC → Samsung** (phone connected over adb: `adb-R5CY21G736B…`). This writes into the user's real Samsung data, so the step **pauses for the user**: they supply a CSV of their real weigh-ins (kept outside the repo, including one row older than 60 days) and do the taps on the phone. This also answers the spec's open check (b).
3. Settings, MetricStatus, source-aware worker. Commit.
4. Wizard and dashboard UI. Commit.
5. Docs (README, PRIVACY bilingual, TERMS, in-app privacy text, sample CSV). Commit.
6. Final verification (below). Fixes found on the device go in separate follow-up commits.

## Verification
- `./gradlew testDebugUnitTest lintDebug assembleDebug` green. The parser tests cover BOM, CRLF, offset and local time, empty cells, unknown and reordered columns, bad rows with line numbers, and a header without `time`.
- On the phone (`adb install -r app/build/outputs/apk/debug/app-debug.apk`):
  1. Fresh install → wizard → CSV → Allow HC (all 5 asked) → step 3 shows the granted types ticked → Finish.
  2. Import the user's real-values CSV (not the repo sample): the report counts are correct, and the dashboard rows show the latest values.
  2b. Edit one value in that file and re-import: Health Connect shows the new value (checks the version rule).
  3. Health Connect app shows the records with this app as the source. Samsung Health shows weight and body fat. Note whether the row older than 60 days reaches Samsung.
  4. Re-import the same file: no duplicates.
  5. Turn Bone mass off and import: no bone-mass records are written, and no permission error appears. Deny a permission twice, then turn the type back on: the app opens the Health Connect settings.
  5b. With the source = HUAWEI, the menu's "Import CSV file" works (HMS not needed for the import).
  6. Switch the source to HUAWEI, then back to CSV: `adb shell dumpsys jobscheduler | grep yourhealthsync` shows the worker scheduled only while the source is HUAWEI.
  7. Privacy statement reachable within 4 taps from a fresh launch.
- Not verifiable yet: the Huawei path on the device (the scopes are still pending approval). This will be stated in the summary.

## Risks
- Samsung may ignore Health Connect records older than ~30 days before its permission grant. If so, the import still fills Health Connect, and the README says so.
- Minute-rounded IDs dedupe across sources only when the CSV time matches Huawei's to the minute. A mismatch leaves duplicates, which can be removed with HC "delete app data" and a re-sync.
