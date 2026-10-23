# Pixel device validation

Testing started 2026-10-23, following technical-views checkpoint `1b2f958`.
The user resumed previously deferred physical testing and authorized the Mac.
Raw screenshots, radio identities, database copies, and CSV contents stay local;
this document records outcomes and counts.

## Confirmed device and installation

Read through ADB **before** installing or running OpenEMF:

- Pixel 9 Pro XL (`komodo`), Android 17, API 37, codename REL.
- Build `CP3A.261005.005`, user/release-keys; security patch 2026-10-05.
- Fingerprint:
  `google/komodo/komodo:17/CP3A.261005.005/16271449:user/release-keys`.
- ADB platform tools 37.0.1.

The current personal profile had no installed `com.openemf.app`. The checkpoint
APK installed successfully and cold-launched into Android's permission controller.
Other profiles were excluded. The permission correction was installed next;
the final APK below was subsequently installed with the Wi-Fi presentation fix.
Same-schema updates retained the test history. No migration was added.

## First-run permission correction

The initial prompt used Android's “make and manage phone calls” wording for
`READ_PHONE_STATE`. The user questioned it. The app never requested `CALL_PHONE`
or `ANSWER_PHONE_CALLS`, but its Phone-state request and cellular gate were
unnecessary for the actual one-shot cell-information API.

The app uses `TelephonyManager.requestCellInfoUpdate(executor, CellInfoCallback)`,
`getAllCellInfo()`, and the no-argument `getSubscriptionId()`. The refresh API
requires precise Location; the no-argument subscription getter does not require
Phone access. This differs from the persistent callback design discussed during
the original source review. See the
[Android API reference](https://developer.android.com/reference/android/telephony/TelephonyManager#requestCellInfoUpdate(java.util.concurrent.Executor,%20android.telephony.TelephonyManager.CellInfoCallback)).

The app was closed without approving the Phone prompt. The manifest request,
runtime request, settings entry, cellular gate, and recording requirement were
removed together. Regression checks assert that the merged manifest requests
none of READ_PHONE_STATE, CALL_PHONE, or ANSWER_PHONE_CALLS and that precise
Location independently authorizes cellular measurements.

On-device cellular refresh then succeeded without Phone permission. Fourteen LTE
observations were retained, with reported RSRP from -107 to -99 dBm. The first
checkpoint capture's Phone-gate denial remains in history as an honest record
of that earlier APK. It is not a failure of the corrected scanner.

## Wi-Fi values outside the expected range

Individual observations and a read-only platform Wi-Fi scan listing contained
raw RSSI values of 0 dBm. A platform listing showed 28 cached access points,
including three zeros; the other values ranged from -46 to -90 dBm. This locates
the zeros in platform results, without establishing a firmware cause or an
independent received-power measurement.

The final APK preserves raw values in Room, technical details, and CSV. Values
outside the existing per-radio scoring ranges are labeled **Out of range**,
sorted behind valid power readings, and excluded from power charts. The index
already rejected these values. Quality metrics expressed in dB can still be zero.

On the Pixel, a saved Wi-Fi source with eight appearances displayed two valid
chart samples and six excluded appearances. Its raw zero row displayed the
out-of-range explanation. The export preserved all 53 zero-valued Wi-Fi rows.

## Performed physical checks

| Check | Observed result |
| --- | --- |
| Wi-Fi / Bluetooth / cellular acquisition | Updated results persisted for all three radios after removing Phone access. One 6 GHz Wi-Fi observation was stored; channel correctness is covered by host tests, without independent RF calibration. Cellular hardware observations were LTE only. |
| Notification denial | Denying the initial notification request left recording stopped with an explanation and no recording service. Allowing the next request enabled explicit recording. |
| Foreground-service enforcement | Service entered foreground with notification 1001 and location/connected-device types `0x18`; Android accepted the declared service and granted permissions. |
| UI-independent recording | Permission-corrected APK: ten captures at a 30-second target, nine with the app in the background. |
| Pause / Resume / Stop notification controls | Pause changed notification controls to Resume; two paused database snapshots both retained nine recording captures. Resume produced a tenth capture. Stop removed the service and closed the journal as STOPPED with an end time. |
| Final APK screen-sleep recording | Three captures at the default 60-second target, two in the background during the sleep test; at least one overlapped the confirmed sleep interval. Power diagnostics reported Dozing, USB powered, `mStayOn=false`. Both background captures recorded Bluetooth PARTIAL with the screen-off limitation, and Wi-Fi/cellular UPDATED. A first sleep attempt woke again; only the confirmed sleep interval supports this result. |
| Final APK controls and stop | Notification Pause and Stop worked; the second session ended as STOPPED. Later service inspection still showed no recording service. |
| Durable history / chart | History survived updates and showed 15 captures and 140 source identities. A Bluetooth source chart rendered ten timestamped samples; Older and the 1-hour window controls were exercised. Wi-Fi out-of-range exclusion was checked separately above. |
| Document-picker CSV export | Saved a new file in personal Downloads through Android's picker. Parsed 68 columns and 561 rows, matching the stopped database: 359 Wi-Fi, 188 Bluetooth, 14 cellular. All rows matched the header width; raw out-of-range values remained present. |
| Storage and current process | Local database integrity check returned `ok`. No fatal AndroidRuntime exception appeared for the final app process during the check. |

The first recording session and detailed notification Resume/denial checks used
the permission-corrected intermediate APK. Screen-sleep recording, notification
Pause/Stop, chart checks, and CSV export used the exact final artifact below.
All test recording was stopped before finishing; the exported file remains in
the phone's personal Downloads folder. A subsequent final manual scan completed
and displayed updated live Wi-Fi readings, with valid negative power sorted
first. The CSV/database counts above describe the snapshot before that scan.

## Final host checks and installed artifact

- `assembleDebug`, `testDebugUnitTest`, and `lintDebug`: successful in 55 seconds;
  182 tests, zero failures/errors/skips; lint zero errors, 68 warnings, one hint.
- See [build log](device-fixes-build.log) and
  [structured results](device-validation.json). Native host frame-timing diagnostic
  lines are omitted from the saved log. An earlier lint attempt hit a Kotlin FIR
  disposed-symbol failure while source edits were still in progress; the complete
  check passed after edits finished, without suppressions.
- Room schema 2 is unchanged; no migration or destructive fallback was added.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 75,964,359 bytes, v2 signature
  verified. SHA-256:
  `24ffeb236d8377c5e56158ed6e43e089f64ba3b7a600af9fb975218cca244342`.
- The installed APK was read back through ADB; its size and SHA-256 matched this
  artifact exactly. No dependency/toolchain upgrades were mixed into these fixes.

## Remaining validation

These are short USB-powered home checks, not a long-duration reliability or
battery benchmark. Android's power-service Dozing display state does not prove
Device Idle / deep Doze operation. Unplugged overnight recording, battery impact,
process-death recovery on hardware, reboot behavior, permission revocation during
recording, actual 5G NR measurements, and a deliberate hardware cached/throttled
scan scenario remain untested. The captures in this run were marked UPDATED or
PARTIAL; cached/freshness policy remains covered by host tests. No calibrated
exposure or measurement-accuracy claim follows from this validation.

## Follow-up: cached/throttled scans (2026-10-23)

The later [radio validation](RADIO_VALIDATION.md) exercised real cached results,
the app cooldown, live expiry, and chart deduplication. Android framework refusal
was not forced. The user deferred interruption and unplugged overnight tests;
NR hardware validation remains pending actual measurements after an LTE-only
plan report. The original APK/hash and counts above remain checkpoint evidence.
