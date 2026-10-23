# Compact radio measurements

2026-10-23. The live Wi-Fi, Bluetooth, and Cellular lists now default to compact
rows instead of tall, expanded summaries. Each row shows the name (or identity
for unnamed sources), power in dBm or its unavailable/out-of-range status, and
the full freshness/cache/age label. A 10 sp footer keeps the measurement type,
full source identity, and radio summary visible: Wi-Fi frequency/channel,
Bluetooth transport/connection state, or cellular technology/registration.
Out-of-range explanations also remain visible in small text. Footer text wraps
as needed without truncation. Long names use an ellipsis. The row expands on
tap to show the original summary, all technical fields, and Signal history;
tapping again collapses it. Stored-history cards retain their existing layout.

Rows use smaller Material typography, a 48 dp minimum tap target, and 4 dp list
spacing. The page heading and outer spacing are smaller, and the results count
explains the tap action. Text can grow vertically with larger system fonts.
Scanning, freshness policy, sorting, storage, export, and dependencies are
unchanged. No ElectroSmart code was adapted.

## Baseline before editing

The existing unchanged-source baseline is recorded in [BASELINE.md](BASELINE.md).
Before this UI edit, checkout `aa09092` also passed `:app:assembleDebug` and
`:app:testDebugUnitTest`: 182 tests, zero failures, errors, or skipped tests;
build successful in 11 seconds. The first sandboxed attempt could not access
the shared Gradle lock; rerunning with approved cache access passed.

Commands use Android Studio JBR and the existing Android SDK:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
    ANDROID_HOME="$HOME/Library/Android/sdk" \
    ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
    --no-daemon --console=plain
```

## Validation

`:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug` passed in
40 seconds for the footer revision: all 182 tests passed, with zero failures,
errors, or skipped tests.
Lint reports zero errors, 67 warnings, and one hint. See the
[build log](compact-signals-build.log); local paths are normalized and native
host frame-timing diagnostics are omitted.

The existing UI check renders a synthetic twelve-network list, verifies that
Wi-Fi and Bluetooth footer information is visible while collapsed, and exercises
Bluetooth expansion and history navigation. The
[reviewed light preview](previews/compact-signals-light.png) shows the updated
compact rows with their small information footers. It is a host render,
not a Pixel screenshot.
The signed 1.1.1/code 3 update was installed through USB on the confirmed
Android 17/API 37 Pixel. Installed bytes match the signed artifact and permission
grants remain present. The user explicitly skipped the post-update visual and
history-count checks; no further phone interaction was performed. See
[RELEASE.md](RELEASE.md) for installation evidence and limits.
