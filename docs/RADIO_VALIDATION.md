# Cached radio and 5G validation

2026-10-23; checkpoint `104a4a2`. Android 17/API 37, Pixel 9 Pro XL,
build CP3A.261005.005 was reconfirmed before these checks. The pass began with the previously validated APK; a later update clarified
the saved cooldown message. No dependency changes were made. Raw database snapshots and diagnostics remain local.

The user deferred interruption handling and unplugged overnight recording.
They enabled 5G during this pass and reported that the SIM plan is LTE-only.

## Rapid repeat and cache provenance

A manual measurement returned Wi-Fi UPDATED with 19 fresh observations. A second
manual measurement began inside the app's 30-second Wi-Fi cooldown. Wi-Fi was
THROTTLED with a retry explanation; all 19 observations were marked cached.
Bluetooth and cellular still returned UPDATED independently.

Comparing the two persisted captures:

- Capture times differed by 14,099 ms.
- All 19 shared Wi-Fi hardware observation timestamps were exactly unchanged.
- Their ages increased from 2,459 ms to 16,558 ms, also exactly 14,099 ms.
- Cached values remained fresh at that time because they were younger than the
  existing 120-second freshness limit. Reading the cache did not reset that age.
- Both local database copies passed `PRAGMA integrity_check`.

This exercised the app's own scan reservation/cooldown on real hardware. It did
not disable Android throttling or force an Android framework refusal.

## Live freshness and chart deduplication

Without another scan, the physical Wi-Fi card showed **cached, stale, 177s old**.
The relative dashboard changed to **Index unavailable**, explaining that stale
and unknown-age values are excluded. The raw capture and power values remained
available. Expiry followed hardware observation time, rather than the cache-read
or capture time.

The physical Wi-Fi history chart showed 18 appearances, 17 unique samples,
one repeated read collapsed, and no excluded appearances for the chosen valid
power source. Thus the cached reappearance stayed in raw history without
creating another plotted hardware sample.

## 5G availability

After the user enabled 5G, both app measurements returned LTE cells only. A
read-only platform diagnostic showed LTE with the NR_NSA display override, but
no CellInfoNr entries and no valid NR signal-strength metrics. All reported NR
SS/CSI metric slots contained Android's unavailable sentinel (2147483647).
These are not measured 5G power values.

Android documents that the display override is for branding/visualization and
cannot establish the actual serving network type. NR_NSA can reflect EN-DC
capability or a secondary NR connection. See the
[TelephonyDisplayInfo reference](https://developer.android.com/reference/android/telephony/TelephonyDisplayInfo#OVERRIDE_NETWORK_TYPE_NR_NSA).
The user reports an LTE-only plan; this may constrain service, but this test did
not independently prove a carrier restriction. No plan or radio-mode changes
were made by the agent. Actual NR identities and SS/CSI measurements remain
unvalidated on hardware until the network supplies them; no values were invented.

## Capture-time message correction

The cooldown path preserved a static “retry in 16s” explanation. Once that
capture aged, the wording could read like a live countdown. The message now
identifies the **app scan limit**, says that the wait was recorded **at request
time**, and explicitly says cached observations are shown. No scan frequency,
freshness policy, storage schema, permission, or dependency changed.

The detailed physical expiry/chart checks above used the preceding validated
APK. The wording update built successfully in 58 seconds: all 182 host tests
passed, lint reported zero errors, 68 warnings, and one hint. See
[build log](radio-build.log) and [structured evidence](radio-validation.json).
The log omits native host frame-timing diagnostics and normalizes local paths.

The updated APK's v2 signature verified, and its installed bytes matched the
host artifact. Size: 75,964,359 bytes; SHA-256:
`c890608fd09fb914013f4d2664f1e4457c590d26387ba3a8513639d4ebed216c`.
Post-update physical message verification is pending phone unlock.
