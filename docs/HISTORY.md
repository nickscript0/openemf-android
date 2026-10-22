# Durable observations and raw CSV

Milestone 3, 2026-10-22, following acquisition and toolchain checkpoint `a6e3dfc`.
This page records history checkpoint `4646f41`. The subsequent
[recording milestone](RECORDING.md) adds session journals in fresh-install
schema 2; the schema-1 snapshot and results below remain historical evidence.

## Fresh-install scope

The user explicitly cancelled the prototype-database migration and confirmed
that all users can be assumed to install for the first time. The agreed plan
has been updated. There is no old-schema migration, data import, or migration
test in this milestone.

Room creates schema version 1 directly in `openemf_signal_history`. This new
name separates the agreed first-install schema from the upstream prototype's
`openemf_database`. No destructive fallback is enabled. The generated schema is
checked into `app/schemas/`; future schema changes, after users have installed
this app, will need their own compatibility decision.

## What is stored

Each acquisition saves its summary, capture metadata, per-radio acquisition
reports, and individual Wi-Fi/Bluetooth/cellular observations in one Room
transaction. A duplicate capture ID is ignored; a failed observation insert
rolls back the whole capture. Acquisition owns saving before it reports success;
UI collectors only display the result. Storage failure is reported to the user.
Live refresh still runs only while the activity is visible.

A source row retains its durable scoped identity, received strength and metric,
original boot-clock observation time, estimated observation UTC time, cache flag,
time basis, freshness window, and age/freshness **at capture**. Capture time is
stored separately. Reading an Android cache again produces a new capture
appearance, carrying the original observation time; it is not represented as a
new RF sample. Unknown timestamps and signal values remain nullable.

Technology fields include Wi-Fi BSSID/frequency/channel/width/standard/security,
Bluetooth address/transport/device type, and cellular technology, subscription,
PLMN, full-width cell identity, channel numbers, reported bands, and the available
LTE/NR signal metrics. NR SS and CSI metrics remain separate. Paired-only
Bluetooth inventory does not create signal observations. Discovery does not
establish a Bluetooth connection.

Capture metadata includes manual/live-refresh mode, optional recording-session
ID, foreground/motion context, capture boot-clock time, and Android's boot count
when available. Current acquisition leaves the recording-session ID empty;
the later service milestone will assign real sessions. Monotonic observation
times are meaningful within a boot; UTC times are estimates based on the phone
clock and may be affected by clock changes. No coordinates are acquired or
added to the raw signal export.

## History and statistics

History reads stored capture summaries and source identities from Room. A saved
capture opens its observations and acquisition reports, including reasons for
failed/empty acquisitions. A source opens a durable timeline; pages of 100 use
a row-ID cursor, with an explicit load-older control. New captures cannot shift
the older-page cursor. Refresh obtains newer captures.

Statistics use stored summaries, with no 100-reading memory buffer and no
separate insertion of the latest UI measurement. This also avoids double-counting
the latest capture. The existing day/week/month UI is retained. Captures with
no fresh measurable signals remain in history, but their unavailable relative
index is excluded from score averages and trends. Whole-history repository
aggregates use SQL and distinguish total capture count from scored count.
Technical history charts remain in the later UI milestone.

Deleting a capture cascades to its raw observations, metadata, and reports.
Clearing history removes those tables together and resets stored place counts.
New scans that complete after a clear can save new captures.

## Raw CSV

Settings → **Export raw signal CSV** opens Android's CreateDocument picker,
requesting local document providers.
The app streams UTF-8 to the selected destination using 500-row database pages
inside a single read transaction. All pages describe one consistent snapshot;
recording/acquisition writes wait for that transaction. Export size is not
limited to the displayed source-history page. The output stream is closed on
success, failure, or cancellation. A failed write is reported as incomplete;
some document providers may leave a partial file.

One row represents one observed source in one capture. Headers name units;
timestamps are ISO-8601 UTC. Missing numeric/string fields are empty, zero is
retained when measured, enum states such as unknown freshness are explicit,
band lists use semicolons, and comma/quote/CR/LF text is quoted and escaped.
Raw strings are retained as supplied. Observation and capture timestamps,
metric names, freshness/cache status, acquisition results, source identity,
and recording context accompany the technical values.

A capture with no sources produces no fabricated raw rows. Its failure/empty
status is still available in capture history. Exporting an empty observation
history produces a header-only CSV. This replaces the old app-directory summary
CSV export. Cloud integration and automatic upload are not implemented.

## Validation

From the repository root, using the already established current toolchain:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ANDROID_HOME=/Users/nsvarich/Library/Android/sdk \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  --continue --no-daemon --console=plain --warning-mode all
```

The final invocation succeeded in **1 minute 33 seconds, all 63 tasks executed**.
**139 tests passed (123 existing, 16 new), zero failures, errors, or skips. Lint:
zero errors, 43 warnings, one hint.** The added dependency is pinned to
Robolectric 4.17 in the version catalog. Its host tests use native SQLite with
an API-35 runtime and a plain test Application, including file-backed Room
close/reopen, 141 durable captures, null/fresh/stale data, all cellular fields,
atomic rollback, replay idempotence, cascade deletion, cursor paging, a 503-row
export crossing page boundaries, concurrent acquisition during export, output
failure recovery, UTF-8 output, and CSV round-trip/locale checks. These are JVM
tests, not phone or emulator results.

The final APK's v2 signature verifies; package metadata confirms min API 26,
compile/target API 37, and version `1.0.0` (code 1). Evidence:

- [Final build/test/lint log](history-build.log), with workspace paths and
  trailing whitespace normalized.
- [Machine-readable results, schema identity, and APK fingerprint](history-validation.json).
- [Fresh-install Room schema](../app/schemas/com.openemf.data.database.OpenEMFDatabase/1.json).

Pixel OS confirmation, physical-device scans, Android document-picker UI
interaction, and screen-off/service recording were not performed. Device
testing remains explicitly deferred. UI compilation and the host file-URI
export test do not establish behavior of the actual phone's document provider.
The existing project/plugin deprecation warnings remain; no lint error was
suppressed. The ElectroSmart reference remained clean and unchanged.

Primary implementation references:

- [Room database testing](https://developer.android.com/training/data-storage/room/testing-db).
- [Android document creation](https://developer.android.com/training/data-storage/shared/documents-files).
- [Robolectric host-test setup](https://robolectric.org/getting-started/).
- [CSV format](https://www.rfc-editor.org/rfc/rfc4180).

The ElectroSmart checkout remains read-only. This storage/export implementation
uses Android/Room APIs directly; no ElectroSmart source is copied.
