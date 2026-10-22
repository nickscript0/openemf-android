# Explicit foreground recording

Milestone 4, 2026-10-22. Starts from the validated history/CSV checkpoint
`4646f41`. The user reports Android 17 on the Pixel; minimum, target, and
compile SDK are API 37. Physical testing remains deferred, including an ADB read
of the installed release/API/build. The minimum-version change is saved
separately in `a29377c`; older Android support is outside scope.

## User controls

The Live dashboard and Settings have a **Background recording** card. Start is
an explicit action in a visible activity. Choose a target interval from 30
seconds to 10 minutes. Recording replaces live refresh while it owns acquisition;
manual/live scans become available after it stops. The main navigation also
shows the current recording phase and a Stop control while recording is active.

Pause cancels and joins the current acquisition before reporting Paused. It
releases the recording wake lease and keeps a foreground notification with
Resume and Stop controls. Resume continues the same service-owned session.
Stop cancels acquisition, finalizes the session journal, removes the foreground
notification, and stops the service. Dismissing the notification requests Stop
when Android permits dismissal. Tapping the notification opens the
app without starting a new recording.

Each notification action is immutable and scoped to a session ID. Old controls
cannot retarget a new session. Duplicate Start commands do not create another
recorder. A pending start cancelled before service delivery cannot be replayed.
A null/replayed service intent has no authority to start acquisition.

## Android execution and permission model

The manifest declares a non-exported service and the `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_LOCATION`, and `FOREGROUND_SERVICE_CONNECTED_DEVICE`
permissions. Promotion happens synchronously before database setup or scanning.
The location service type covers location-sensitive Wi-Fi/cellular scan access;
the connected-device type is added when Bluetooth is included. These execution
types do not assert that a discovered device is connected. Recording uses the typed foreground-service path directly; no
older-Android path is maintained.

Recording requires precise Location permission and Android Location services.
The session selects the authorized radios once at Start: Wi-Fi, cellular when
phone-state permission is granted, and Bluetooth when its required permissions
are granted. The card and notification show the selected radios. Newly granted
radio permissions take effect in a new session. Existing independent live-scan
permission behavior is retained.

The app requests notification permission when the user chooses Start. It requires
notifications and the recording channel to be enabled so the controls remain
available; Android itself can allow a foreground service without notification
permission. A service-side guard checks selected scan permissions, Location
services, and notification availability while running and paused. Losing a
requirement cancels work and records the reason. Denial reported by a scanner
also ends the session after the saved acquisition result.

No `ACCESS_BACKGROUND_LOCATION`, exact-alarm, battery-exemption, or boot
permission is requested. The service returns `START_NOT_STICKY`, is not bound to
the UI, and is not stopped merely because the activity or task closes. There is
no boot receiver, job, alarm, or saved enabled flag that restarts recording.
Android Task Manager stop, force stop, and process death may end it without a
callback. After termination, a new explicit Start in the app is required.

## Acquisition, power, and timing

Only the service's active session can invoke the background acquisition path.
The existing acquisition mutex serializes all scans, and acquisition still owns
the atomic Room save. Each recording capture has `CaptureMode.RECORDING`, its
session ID, and the actual UI foreground/background context. A stopped/paused
session cannot authorize another scan. Cancellation before persistence releases
scan resources; a transaction already committing at a cancellation boundary may
finish atomically.

While recording, Wi-Fi retains the rolling four-attempt/two-minute budget and
30-second spacing. AOSP classifies a foreground-service process within that
foreground budget; Android remains authoritative and can refuse scans. A
Wi-Fi restriction does not prevent Bluetooth or cellular acquisition. Cached
readings retain original observation times and freshness provenance.

BLE discovery remains unfiltered. Android can suspend unfiltered BLE scans with
the screen off. A window that observes screen-off is explicitly Partial, and
both the card and saved acquisition report explain that limitation. No wildcard
filter or privileged scan mode is used to evade the platform restriction.
Classic discovery still depends on devices being discoverable.

Explicit unpaused recording uses a partial wake lock to support screen-off
sampling. Each interval has a bounded lease renewed by the recording loop, with
pause/stop/failure/service-destruction cleanup. Its timeout is the target interval
plus 60 seconds, capped at 11 minutes. The UI warns about battery use. Paused
recording holds no wake lock. No Wi-Fi lock or power-management exemption is
used. Doze, radio firmware, and system termination can still delay or interrupt
sampling; intervals are targets, and overdue attempts are not queued for replay.

## Durable sessions and local data

Room schema 2 adds `recording_sessions` in the existing local history database.
The user-approved fresh-install assumption remains in force: schema 2 is created
directly, with no migration, prototype import, or destructive fallback. The
schema-1 snapshot and its earlier evidence remain in Git for traceability.

Session start, pause/resume, clean stop, and failure have durable journal states.
Capture links, timestamps, and selected radios survive UI recreation. On a new
process, unfinished session journals become Interrupted with an unknown end
time; no stop time is fabricated. A failed final journal write is surfaced,
work still stops, and a later process/start can reconcile the unfinished entry.
The UI active flag always comes from the current service, never a journal row.

The live counter tracks completed recording captures. Retained capture counts
come from Room and can differ after deletion or an interrupted commit. Raw CSV
already includes session IDs and capture mode. The existing paged, transactional
export continues to work while recording; acquisition saves may wait for that
read transaction. Clear History requires recording to stop, with a second guard
inside the repository transaction to cover a concurrent Start. Clearing also
removes recording journals. Individual capture deletion remains available.

Automatic Android app backup is disabled for the local-only scope, with modern
data-extraction exclusions for cloud, device, and cross-platform transfers. Recording
adds no coordinate collection or cloud transport. The ElectroSmart checkout
remains read-only; this service implementation is written against public Android
APIs without copying ElectroSmart source.

## Host validation

The debug build and all **167 tests** pass (139 prior tests plus 28 recording
checks). Robolectric Android contract and file-backed Room tests now run on
**API 37**, using the existing Robolectric 4.17 dependency. Lint has **zero
errors, 73 warnings, and one hint**. Raising the minimum SDK makes inherited
version guards redundant: 35 warnings are `ObsoleteSdkInt`; the increased warning
count does not require adding compatibility paths.

Reproduction command (Android Studio JBR 25.0.3, SDK installed locally):

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
    ANDROID_HOME="$HOME/Library/Android/sdk" \
    ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
    --continue --no-daemon --console=plain --warning-mode all
```

[Build output](recording-build.log) and [validation evidence](recording-validation.json)
record the exact result, suite counts, lint findings, schema fingerprint, and APK
hash/signature. The APK is `app/build/outputs/apk/debug/app-debug.apk`, for first
installation on Android 17 or newer.

The suite includes host checks for command authorization,
notification intent/channel/manifest contracts, cancellation and pause cleanup,
requirements loss while paused, failed acquisition/journal writes, wake leases,
session-linked raw export, and interrupted-journal reconciliation.

These tests simulate lifecycle boundaries and Android contracts on the host.
They do not exercise Android's actual foreground-service enforcement, physical
radio discovery, notification drawer, CPU sleep, Doze, or force-stop behavior.

Before device testing, confirm the Pixel model, installed Android release, API,
build fingerprint, and patch level. Then validate visible Start, denied and
revoked permissions, channel blocking, activity recreation/task dismissal,
notification Pause/Resume/Stop/dismissal, screen-off sampling, power behavior,
Task Manager stop, process death/reopen, and session/raw-CSV integrity. None of
these physical-device checks has been performed.

Primary references checked during implementation:

- [Foreground service launch](https://developer.android.com/develop/background-work/services/fgs/launch).
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types).
- [Visible-activity and while-in-use restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- [Wi-Fi scan limits](https://developer.android.com/develop/connectivity/wifi/wifi-scan).
- [AOSP Wi-Fi foreground-service classification](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/ScanRequestProxy.java)
  (reviewed file blob `38cf93767afea67b68ddbce30f63bd790d943088`).
- [BLE screen-off scan restrictions](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner).
- [Wake locks](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock).
- [Task Manager stop](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).
- [Android 17 release / API level 37](https://android-developers.googleblog.com/2026/06/Android-17.html).
- [Android app backup](https://developer.android.com/identity/data/autobackup).
