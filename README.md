# OpenEMF: Pixel home signal monitor

An open-source Android app for Wi-Fi, Bluetooth, and cellular signal detection
and measurement. This fork develops a technical home-monitoring workflow for a
Pixel 9 Pro XL, using Kotlin, Compose, Hilt, and Room.

Forked from [apmai/openemf-android](https://github.com/apmai/openemf-android).
See [the agreed plan](docs/PLAN.md) and [baseline evidence](docs/BASELINE.md).

## Current capabilities

- A technical Signals home screen with per-radio tabs, received power and quality,
  live freshness, name/identity filtering, and expandable details.
- Wi-Fi observations with BSSID, received power, frequency, corrected 2.4/5/6 GHz
  channels, width, standard, and security information.
- Bounded BLE and Classic Bluetooth discovery. Paired devices are separate
  inventory; pairing does not produce an RSSI or imply a connection.
- Default-subscription cellular observations, including full 36-bit 5G NCI and
  reported NR SS/CSI power and quality metrics.
- Per-radio acquisition status, explicit missing values, observation timestamps,
  cached-data provenance, and freshness indicators.
- A separate relative received-signal dashboard. Stale, unknown-age, and unavailable
  measurements do not enter the current index. The index is not a calibrated
  exposure or safety measurement.
- Live refresh while the app is visible, respecting Android scan limits.
- Explicit background recording through a foreground service, with notification
  Pause/Resume/Stop controls and durable session metadata. Interrupted sessions
  do not restart automatically. See [recording notes](docs/RECORDING.md).

Individual signal observations and capture summaries are stored in Room.
History includes saved captures, acquisition status, and paged per-source
timelines. Source charts separate RSSI/RSRP power from LTE/NR quality metrics,
use real observation times, and collapse repeated cache reads. Select 1-hour,
24-hour, or 7-day windows and inspect individual samples. Relative-index trends
read durable captures. Settings exports raw signal CSV through Android's document
picker. See [technical views](docs/TECHNICAL_VIEWS.md) and [history notes](docs/HISTORY.md).

This development version assumes fresh installations for all users, as agreed;
it creates the current database schema directly and does not upgrade earlier
development databases. The next validation step is testing on the confirmed
Pixel when device testing resumes. Maps, coordinate recording, cloud services, and store
publishing are deferred. Automatic app backup/transfer is excluded; raw CSV
export is user-initiated.

## Build and checks

Use the Gradle wrapper and a compatible JDK. The development setup uses Android
Studio's JBR 25, Gradle 9.8.2, AGP 9.4.1, and Android SDK API 37. The minimum
Android version is 17 (API 37), matching the user's Pixel. Older Android releases
are outside this fork's scope. Set `JAVA_HOME` and `ANDROID_HOME` for your installation.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The personal-install debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

[Acquisition notes](docs/ACQUISITION.md) describe the implementation, test coverage,
and remaining limits. [Toolchain notes](docs/TOOLCHAIN.md) record the separate
upgrade and reproducible commands.

## Device validation

The user reports Android 17 on the Pixel. Device testing is currently deferred.
The Pixel's installed Android release and
API level must be read from the actual phone before installing or testing an APK.
Build success and local tests do not establish hardware scan quality or reliable
background recording.

The app requests Android location permission for Wi-Fi/cellular scan APIs,
phone-state permission for cellular updates, and Bluetooth scan/connect
permissions. These permissions are evaluated per radio; denying
one does not fabricate measurements or block independently authorized radios.
Android Location services may also need to be enabled. Scans cannot bypass
Android or hardware limits.

## License and reference

[BSD 3-Clause](LICENSE). Retains OpenEMF attribution and existing acknowledgments
of [ElectroSmart](https://github.com/arnaudlegout/electrosmart). The user's
[ElectroSmart reference branch](https://github.com/nickscript0/electrosmart/tree/improved-device-names)
was kept read-only; provenance is described in the acquisition notes.
