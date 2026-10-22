# Acquisition correctness

Milestone 2, 2026-10-22. Starts from the recorded baseline at `46925c5`.
Device testing remains explicitly deferred; the Pixel OS is unconfirmed.

## Permissions and independent radio results

The runtime request includes `READ_PHONE_STATE`, and cellular acquisition checks
it alongside location before requesting updates. Permission status is refreshed
after permission dialogs, on return to the activity, and while the app runs.
Each radio reports its own result: completed, partial, cached, limited, refused,
timed out, denied, disabled, unavailable, or failed. A denied phone permission
does not block Wi-Fi or independently authorized Bluetooth.

Wi-Fi scanning uses its documented location requirements; the unrelated nearby
Wi-Fi permission is no longer requested or presented as necessary. Android
Location-services state is checked separately from permission grants.

## Observation time and relative index

Each source carries its observation boot-clock time, an estimated UTC time,
cache provenance, timestamp basis, and freshness window. Reading a cache does
not advance the observation time. Invalid, unavailable, or future timestamps
remain unknown. Wi-Fi and cellular readings expire after 120 seconds; Bluetooth
readings after 30 seconds. These are explicit app freshness policies, not
guarantees about radio accuracy.

Wi-Fi, BLE, and cellular hardware timestamps are retained. Classic discovery
provides no hardware timestamp, so its timestamp is marked as event receipt.
The dashboard updates age/freshness each second. Stale, unknown-age, and
unavailable-strength values do not enter the current relative index. Without
fresh measurable signals the index is unavailable. Signal values lead the
source rows, with units and acquisition details available alongside them.

Missing strength/quality values are nullable; Android's unavailable sentinels
are never displayed as measured values. The relative received-signal index is
explicitly distinguished from a calibrated exposure or safety measurement.

## Scans and lifecycle

- Wi-Fi scan broadcasts have a 12-second deadline and receiver cleanup on
  completion, failure, cancellation, or timeout. Refused requests and failed
  broadcasts retain explicitly cached data with the original observation times.
- Wi-Fi uses atomic reservations, a rolling four-attempt/two-minute budget, and
  at least 30 seconds between active requests. Background reservations have a
  conservative 30-minute interval; OS throttling remains authoritative.
- Wi-Fi limits affect Wi-Fi only. A measurement still acquires Bluetooth and
  cellular data when the Wi-Fi budget is unavailable.
- Bluetooth uses a bounded five-second window for BLE advertisements and
  discoverable Classic devices. It retains the latest reading, including weaker
  readings, and rejects out-of-order batches. It stops its BLE scan, unregisters
  its receiver, and cancels Classic discovery only when it started discovery.
  An existing Classic discovery is observed without taking ownership.
- Cellular uses an eight-second `requestCellInfoUpdate` deadline on API 29+.
  Older supported Android versions provide explicitly cached results. There is
  no per-request executor thread or long-lived telephony listener to leak.
  Android has no cancellation API for this request; late callbacks are ignored.
- Coordinator deadlines isolate radio failures. Parent cancellation propagates
  through the scan helpers and releases registered resources.
- Magnetometer averaging and instantaneous reads also have deadlines and
  single-completion guards.

Live refresh runs only while the activity is visible. Its settings toggle
reflects the active engine, rather than a persisted flag that could imply a
running recorder after process restart. Foreground-service recording remains
milestone 4; this change does not claim reliable background recording.

## Identities and technical fields

Wi-Fi identity uses BSSID and Bluetooth identity uses the observed address.
Android/device privacy can rotate Bluetooth addresses; these IDs do not prove a
permanent physical-device identity. Paired devices are separate inventory,
without invented RSSI, connection state, or index contribution.

Cellular identity includes technology, default subscription, original PLMN
strings, and cell identity. GSM/UMTS also use the location area. PCI/channel are
fallback identity fields only when the full cell identity is unavailable, so
radio reconfiguration does not split a known LTE/NR cell's history. Full 36-bit NR
NCI is retained as `Long`, including valid values that resemble the old Int
sentinel. NR SS/CSI RSRP, RSRQ, and SINR remain distinct, nullable fields, with
NR-ARFCN and reported bands. NR uses Android's SS-RSRP range. No LTE eNodeB
identity is inferred for NR.

LTE MCC/MNC access is guarded for API 26/27, fixing the baseline lint errors.
PLMN strings preserve two- versus three-digit MNC, including leading zeros.
Bands come from platform reports instead of ambiguous/incorrect ARFCN guesses.
LTE RSSNR retains the raw Android value and a normalized dB value: Android 11
changed the getter from tenths of dB to whole dB.

Cellular acquisition currently uses the default subscription. Multi-SIM coverage,
CDMA/TD-SCDMA support, and modem-specific availability require later work and
device validation. Missing metrics are displayed as unavailable.

## Reference and attribution

ElectroSmart's `origin/improved-device-names` at `59dce84` was read as a reference
for richer cellular information and asynchronous refresh behavior. That checkout
was not changed. The acquisition code here is independently implemented against
Android's public APIs; no ElectroSmart source was copied. Existing attribution
in the score calculator and the upstream BSD license are retained.

Primary references:

- [Android Wi-Fi scanning and limits](https://developer.android.com/develop/connectivity/wifi/wifi-scan).
- [Cell-information callback permissions](https://developer.android.com/reference/android/telephony/TelephonyCallback.CellInfoListener).
- [NR identity](https://developer.android.com/reference/android/telephony/CellIdentityNr).
- [NR metrics](https://developer.android.com/reference/android/telephony/CellSignalStrengthNr).
- [BLE observation timestamps](https://developer.android.com/reference/android/bluetooth/le/ScanResult).
- [Android 10 LTE RSSNR implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/telephony/java/android/telephony/CellSignalStrengthLte.java).
- [Android 11 LTE RSSNR implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/telephony/java/android/telephony/CellSignalStrengthLte.java).

## Host validation

The acquisition change is verified before the separately authorized toolchain
modernization. The old Gradle configuration cannot run the newly installed
Android Studio Java 25 runtime. The already installed JBR 21 is used for this
checkpoint; no project dependency/SDK versions are changed in this commit.

```sh
env JAVA_HOME=/Users/nsvarich/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home \
  ANDROID_HOME=/Users/nsvarich/Library/Android/sdk \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  --continue --no-daemon --console=plain
```

The complete invocation succeeded in 52 seconds. **123 tests passed (87 existing
and 36 new), zero failed or skipped. Lint: zero errors, 67 warnings, one hint.**
The baseline API compatibility errors are fixed without lint suppression.
The debug APK's v2 signature verifies. Preserved evidence:

- [Build/test/lint console output](acquisition-build.log), trailing whitespace normalized.
- [Suite counts, lint counts, versions, and APK hash](acquisition-validation.json).

The tests cover deterministic permission policy, channel mappings, source
identity/metric boundaries, freshness/scoring, rolling and concurrent scan
reservations, callback deadlines/cleanup, cancellation, and radio failure
isolation. They do not exercise Android radio hardware or OS permission dialogs.

No APK was installed or launched. No screen-off, scan-quality, battery, or Pixel
compatibility result is claimed. Device validation still requires reading the
Pixel's actual Android release and API level before any installation/test.

Individual source persistence, raw CSV, and the foreground recording service
remain subsequent milestones. The existing Room summary schema is unchanged.
