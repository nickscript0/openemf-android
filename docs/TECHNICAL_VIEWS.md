# Technical signal views and durable charts

Milestone 5, 2026-10-22, following foreground-recording checkpoint
`b170e06`. Minimum/target/compile SDK remain API 37 for the user's Pixel on
user-reported Android 17. Physical testing remains deferred. Read the phone's
actual release/API/build before installation or device tests.

## Workflow

Signals is the default destination, with Wi-Fi, Bluetooth, and Cellular tabs.
Each capture shows the radio's acquisition status, missing permissions, scan
budget, and selected signals. Filter by name or source identity and sort by
received power or name. Missing power remains Unavailable. Freshness advances
using the boot clock while the screen is open; cached, stale, and unknown-age
readings remain labeled.

Technical cards lead with the measured quantity and its units. Details include
Wi-Fi BSSID/channel/frequency/width/security, Bluetooth address/transport and
unknown connection state, and available cellular identities, bands, channels,
registration, subscription, and LTE/NR power and quality. Full 36-bit NCI is
preserved. Pairing stays separate from received-signal observations. Cellular
coverage remains the default subscription reported by the existing acquisition
layer.

Signal history opens directly from a live card or the searchable Sources tab
in stored History. Recording options remain available on Signals and the
relative dashboard; active recording always has a main-navigation Stop control.
Full recording and raw CSV controls remain in Settings. UI state never starts
acquisition or recording by itself.

The separate Relative index destination retains a gauge and contribution
breakdown. It excludes stale/unknown-age/unavailable inputs. Its arbitrary
threshold ticks and Excellent/Good legend are hidden; health-advice and external
website tabs are removed from main navigation. The index remains explicitly
relative, with different RSSI and reference-signal metrics combined
heuristically; it is not calibrated exposure, dose, or a safety threshold.

## Charts and provenance

Each source has 1-hour, 24-hour, and 7-day chart windows, independent of the raw
history's 100-row keyset pager. A reactive Room query reads at most 2,001 capture
appearances; the UI plots from the newest 2,000 and explicitly discloses overflow.
A shorter window or raw CSV can expose data outside that bounded plot.
No schema change or migration is introduced.

Select RSSI/platform power, LTE RSRP/RSRQ/RSSNR, or reported NR SS/CSI power and
quality metrics. Power uses dBm; quality uses dB. Metrics are never overlaid or
silently treated as the same quantity. Charts show separate dots with real
elapsed spacing on the time axis and labeled, auto-scaled value axes. No line
bridges a pause, missing sample, or interval without an observation.

Only fresh-at-capture, numeric, known-time observations within the window
produce dots. Missing values, stale or unknown-time readings are excluded rather
than replaced with zero. Cache appearances share their original observation
time. Repeats collapse by source/metric/boot count/boot timestamp when available,
or by their wall timestamp otherwise. Equal boot times from different boots
remain distinct. The latest retained appearance supplies provenance for a
collapsed sample; raw history and CSV retain every appearance.

Hardware wall times remain estimates derived from Android's boot clock.
Bluetooth event-receipt timestamps describe delivery time. The selected dot
shows the value, local time, time basis, mode, and cache state; Older/Newer
controls expose the same selection without a precise tap. Axis ranges and
sample counts have accessibility semantics. Missing/error/loading states are
textual, and units never depend on color.

Charts follow persisted inserts/deletions and refresh their moving window each
minute while subscribed. Navigation collects with the visible activity's
lifecycle so stopped UI collectors release chart subscriptions after a short
timeout; recording continues in its service. Raw pages are explicitly a snapshot, refreshed by the
user. A source-specific ViewModel owns query/page state across activity
recreation and cancels superseded requests; it owns no recording work.

Saved Trends shows the relative index at capture time from durable summaries.
Its time axis uses capture time, the value axis is fixed at 0–100, unavailable
captures are counted but not plotted as zero, and all selected-window captures
contribute to summary counts. Plotting is bounded to the newest 2,000 scored
captures with disclosure. The dots and arithmetic index mean have no health
interpretation.

## Validation

The debug build and all **181 host tests** pass (167 prior tests plus 14 new
checks). Lint reports **zero errors, 69 warnings, and one hint**. Checks cover
timestamp/cache handling, metric units and missing data, actual Room query
limits/reopen/reactivity, Compose radio/navigation/selection behavior, and host
rendering in light and dark themes. All Android/Room/Compose host checks use API
37. Schema 2 is byte-for-byte unchanged.

Reproduce with Android Studio JBR 25.0.3 and the existing SDK:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
    ANDROID_HOME="$HOME/Library/Android/sdk" \
    ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
    --continue --no-daemon --console=plain --warning-mode all
```

[Build output](technical-build.log) and [validation evidence](technical-validation.json)
record the exact results, suites, dependency versions, unchanged schema
fingerprint, and verified APK v2 signature/hash. The log normalizes workspace
paths and omits explicitly counted native host frame-timing diagnostics.
Dependency setup is saved separately in `327d800`; no SDK/toolchain downgrade or
older-Android workaround is used. The personal-install APK is
`app/build/outputs/apk/debug/app-debug.apk`.

Reviewed host previews: [Signals, light](previews/signals-light.png),
[relative dashboard, light](previews/relative-index-light.png), and
[NR quality chart, dark](previews/nr-quality-dark.png).

Compose UI tests use the existing Compose BOM and Robolectric API 37 native
graphics. The added test harness follows the
[official Compose testing setup](https://developer.android.com/develop/ui/compose/testing).
Espresso is pinned to stable 3.7.0 instead of the Compose test library's
transitive 3.5.0; its [release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)
include removal of the reflective InputManager call that fails on API 37.
Host images are simulated layouts, not screenshots from the Pixel. No emulator,
physical scan, screen-off recording, notification drawer, accessibility service,
or document-picker test has been performed.

The SDK/toolchain, foreground-service design, acquisition limits, CSV schema, and
fresh-install assumptions are unchanged. ElectroSmart remains read-only; no
reference code is copied for this milestone.
