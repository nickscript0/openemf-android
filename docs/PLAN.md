# Pixel home signal monitor

## Agreed scope

Build a modern open-source Android app for Wi-Fi, Bluetooth, and cellular signal
detection, technical measurement, and durable history. The primary device is a
Pixel 9 Pro XL running stock Android. **Read and record its installed Android
version and API level before any device testing.**

The app is primarily for home use, with live measurements and background
recording that the user explicitly starts and stops. Technical signal readings
come first. Retain an exposure-style dashboard, clearly described as a relative
received-signal index rather than a calibrated exposure or health measurement.

Use personal APK installation and local storage first. Defer maps, coordinate
recording, cloud services, and store publishing. Preserve Kotlin, Compose, Hilt,
and Room. Respect Android scan limits and clearly label unavailable, cached,
stale, and incomplete observations; never manufacture signal strength or
connection state.

## Repository and reference

- Workspace: `/Users/nsvarich/Documents/dev/openemf-android`.
- GitHub fork: `nickscript0/openemf-android`.
- `origin`: the user's fork; `upstream`: `apmai/openemf-android`.
- Working branch: `codex/pixel-home-monitor`.
- Inspect existing files, Git state, remotes, and branches before creating them.
- Keep `/Users/nsvarich/Documents/dev/electrosmart` as a read-only reference.
  All previous user changes are on `improved-device-names` (available locally as
  `origin/improved-device-names`); do not switch or modify that checkout.
- Prior source comparison: OpenEMF `master` at `253731a` and ElectroSmart
  `improved-device-names` at `59dce84`. That comparison did not clone, build, or
  device-test either project; ElectroSmart was already present locally.
- Adapt richer ElectroSmart cellular and recording logic selectively, preserving
  its copyright, license, attribution, and relevant provenance.

## Ordered implementation

### 1. Establish the unchanged baseline

- Save this plan and add an `AGENTS.md` pointer before implementation.
- Inspect whether the fork, checkout, branch, Java runtime, Android SDK, and
  connected device already exist; reuse appropriate existing setup.
- Obtain OpenEMF, set the requested remotes and branch, and record the exact
  upstream baseline revision (including any difference from `253731a`).
- Build unchanged application sources and run all existing local tests.
- Record exact commands, tool versions, test counts, build results, artifact
  locations, and any pre-existing failures or missing prerequisites.
- Do not upgrade dependencies/SDKs or change functionality to make a baseline
  appear successful. Document any necessary build-only repair separately.
- Confirm the phone's model, installed Android release, API level, and build
  before installing or running any APK. If no device is available, record that
  limitation without claiming device validation.

### 2. Correct signal acquisition

- Request runtime `READ_PHONE_STATE` before registering cellular callbacks, with
  permission denial/revocation handled honestly.
- Preserve full-width 5G NR cell identities and expose available NR metrics.
- Correct Wi-Fi channel calculations, especially 6 GHz.
- Bound scan lifetimes, release listeners/resources, respect scan throttling,
  and distinguish scan failures from successful empty scans.
- Track observation timestamps and cached-result freshness.
- Treat BLE discovery, paired devices, and known connections separately; remove
  invented `-70 dBm` readings and assumed connection state.
- Add focused checks for correctness, permission behavior, and timeouts.

### 3. Persist individual histories and export raw CSV

- Store timestamped individual Wi-Fi, Bluetooth, and cellular observations in
  Room with durable source identities, technology-specific fields, freshness,
  and recording-session context.
- Preserve existing local data through an explicit schema migration.
- Query histories and statistics from durable storage, removing reliance on the
  last 100 in-memory measurements.
- Export raw observations as correctly escaped CSV through Android's document
  picker; keep missing values empty and retain units and measurement times.

### 4. Record independently of the UI

- Use an appropriately declared Android foreground service, started explicitly
  from a visible UI with the permissions and service types required by the
  installed/target Android versions.
- Provide a persistent recording notification with stop/pause controls as
  appropriate, and a clear recording status in the app.
- Keep acquisition and persistence alive while the UI is stopped; stop cleanly
  on explicit user action and permission loss. Avoid automatic boot recording.
- Validate lifecycle behavior, screen-off recording, and notification controls.

### 5. Provide technical views and the relative dashboard

- Provide technical signal lists, per-source details, and durable history charts
  with explicit units and unavailable/stale indicators.
- Retain a clearly explained relative received-signal dashboard alongside the
  technical views, with no claims of calibrated exposure or safety thresholds.
- Keep recording, export, and permission states understandable.

### 6. Upgrade SDK/toolchain separately

- Only after a working baseline is established, evaluate SDK, Android Gradle
  Plugin, Gradle, Kotlin, Compose, Hilt, and Room updates as a separate change.
- Follow current Android foreground-service, permission, and scanning rules.
- Rebuild, rerun appropriate checks, and validate on the confirmed Pixel OS.

## Completion evidence

Keep progress and reproducible evidence in `docs/BASELINE.md` and subsequent
focused implementation notes. Separate build/test results from device results.
Each implementation milestone should explain the change, validation, and
remaining limitations. Never report hardware scan quality, screen-off recording,
or phone compatibility as verified without actually exercising them.

## Initial inspection (2026-10-22)

- The requested workspace exists and is empty; it is not yet a Git repository.
- The connected GitHub account cannot find `nickscript0/openemf-android` (404);
  verify with the local authenticated GitHub CLI before creating the fork.
- Upstream exists and its default branch is `master`.
- ElectroSmart is clean on `main` at `e069644`; its local remote-tracking
  `origin/improved-device-names` resolves to
  `59dce84729b464f9253186627b3195df1b0e86f1`.
- Android Studio is installed. Java, SDK, build, and device readiness still need
  to be established.

## Progress

- Plan and `AGENTS.md` pointer saved before repository creation.
- Fork created and verified; requested remotes and local branch configured at
  the original reviewed upstream commit. ElectroSmart remains untouched.
- Unchanged application compilation/package succeeded. Original test compilation
  failures and subsequent test-only repairs are recorded in
  [BASELINE.md](BASELINE.md). All 87 tests now pass; APK signature and unchanged
  binary hash verified. Existing lint findings: 2 errors, 65 warnings, 1 hint.
- No acquisition, storage, recording, UI, or toolchain changes have started.
- The user explicitly deferred device testing. Installed Pixel Android version
  and device validation remain pending; no APK has been installed or run.
