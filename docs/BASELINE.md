# Unchanged OpenEMF baseline

Date: 2026-10-22 (America/Halifax). This checkpoint precedes acquisition,
storage, recording, UI, and toolchain changes in [PLAN.md](PLAN.md).

## Source and repository

- Upstream: <https://github.com/apmai/openemf-android> (`master`).
- Exact baseline: `253731a021c27ec0df3b4c97e74096ce9d38b5b8`, matching the
  previously reviewed `253731a`.
- Fork: <https://github.com/nickscript0/openemf-android>, created after confirming
  it did not already exist through both the connector and authenticated CLI.
- Workspace: `/Users/nsvarich/Documents/dev/openemf-android`.
- Branch: `codex/pixel-home-monitor`, created from `upstream/master`.
- `origin`: `https://github.com/nickscript0/openemf-android.git`.
- `upstream`: `https://github.com/apmai/openemf-android.git`.
- The workspace was initially empty. The plan and `AGENTS.md` were written
  before fetching and checking out the unchanged upstream sources.
- ElectroSmart remains untouched on its original `main` checkout; its local
  `origin/improved-device-names` is
  `59dce84729b464f9253186627b3195df1b0e86f1`. No reference code has been copied.

To verify that application sources and dependency configuration remain unchanged
after the test-only repairs below:

```sh
git diff --exit-code 253731a021c27ec0df3b4c97e74096ce9d38b5b8 -- \
  app/src/main app/build.gradle.kts app/proguard-rules.pro \
  build.gradle.kts settings.gradle.kts gradle.properties gradle
```

## Build environment

| Component | Baseline value |
| --- | --- |
| Host | macOS, Apple Silicon (arm64) |
| JDK | Android Studio bundled JetBrains OpenJDK 17.0.11+0 |
| Java home | `/Applications/Android Studio.app/Contents/jbr/Contents/Home` |
| Android SDK | `/Users/nsvarich/Library/Android/sdk` |
| Installed platforms | API 31, 34, 35 |
| Installed build tools | 30.0.3, 34.0.0, 35.0.0 |
| Gradle wrapper | 8.13 (`gradle-8.13-bin.zip`) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / Compose compiler plugin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Compose BOM | 2024.12.01 |
| Hilt | 2.53.1 |
| Room | 2.6.1 |
| Minimum / compile / target SDK | 26 / 35 / 35 |
| ADB | 1.0.41, platform-tools 35.0.2-12147458 |

The system `java` launcher has no configured JDK; the existing Android Studio
runtime is used explicitly. No global Java settings, SDKs, or dependency
versions were changed. Gradle and Maven dependencies may be downloaded to the
normal user cache. The SDK already contains API 35.

Upstream commits `gradlew` as mode `100644` (not executable). Invoke it through
`/bin/sh`; the documented upstream `./gradlew` invocation would fail until its
executable bit is repaired separately. The wrapper was not edited for the exact
upstream run. Its executable bit was then repaired; its contents are unchanged.
The generated `.kotlin/` cache was also added to `.gitignore`.

## Reproduce the baseline

Run from the repository root:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ANDROID_HOME=/Users/nsvarich/Library/Android/sdk \
  /bin/sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  --continue --no-daemon --console=plain
```

The original run omitted `--continue`. That option was added for the subsequent
run so test failures would not prevent lint from reporting its own findings.
Console output and final results are preserved in the evidence files below.

## Existing local tests

The upstream source contains 87 `@Test` methods in five suites:

| Suite | Test methods |
| --- | ---: |
| `EScoreCalculatorTest` | 32 |
| `ExposureIndexTest` | 16 |
| `RegulatoryGuidelinesTest` | 12 |
| `ScanSchedulerTest` | 14 |
| `SourceDetailsTest` | 13 |
| Total | 87 |

These are local JVM tests of scoring, guidelines, scheduling, and source models.
They do not validate actual Android radio acquisition, permissions, Room
migrations, service lifecycle, or screen-off recording. Upstream has no
`app/src/androidTest` sources.

## Results: exact upstream sources

The first invocation failed after 3m 37s at
`:app:compileDebugUnitTestKotlin`. **No unit tests executed.** The compiler found:

- `RegulatoryGuidelinesTest.kt:228` and `:256`: obsolete `EScoreResult` fixtures
  pass a removed `totalExposureQuotient` parameter and omit the required `level`,
  `cumulativeDbm`, `cumulativeMw`, and `sourceCount` parameters.
- `ScanSchedulerTest.kt:111`: the backtick test name contains `1.5x`; Kotlin's
  JVM backend rejects the period in a method name.

Application compilation and `:app:packageDebug` succeeded and generated the
debug APK. Because the requested task graph failed, a separate invocation will
verify the `assembleDebug` lifecycle task and obtain the final lint report.

The unchanged-source APK is
`app/build/outputs/apk/debug/app-debug.apk`, application ID `com.openemf.app`,
version `1.0.0` (code 1), minimum API 26. Its initial SHA-256 is
`9aa61262bbeb6fad3e23711a7bb3ff68f18106a535377af7c03e25aae3a46f53`.

These failures were recorded in the first documentation commit before test
repairs. Application behavior and SDK/toolchain versions remain unchanged.

## Test-only repair and final verification

The first compatibility repair replaced the two obsolete score fixtures with
scores calculated from their existing source fixtures, and renamed the test
containing a period. No assertions or tests were removed. All 87 tests then
executed: **84 passed, 3 failed**.

The three failures were invalid input fixtures in `EScoreCalculatorTest`:

- The score-at-0 and score-at-+10 tests supplied individual Wi-Fi readings of
  0 and +10 dBm. Both are outside the existing accepted Wi-Fi range of
  -150 through -1 dBm, so the calculator filtered them out.
- The equal-power test supplied LTE at -40 dBm, outside the existing accepted
  LTE range of -140 through -43 dBm, leaving only its Wi-Fi source.

The repaired fixtures retain the original score and 50/50 breakdown assertions:
10 and 100 distinct valid Wi-Fi sources at -10 dBm aggregate to 0 and +10 dBm,
respectively; equal Wi-Fi and LTE sources at -50 dBm are valid for both
technologies. Additional assertions verify source counts and aggregate dBm.
The production calculator and its accepted ranges were not changed.

Final verification:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ANDROID_HOME=/Users/nsvarich/Library/Android/sdk \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
```

**BUILD SUCCESSFUL in 14s. All 87 tests passed, with zero skipped tests.**
All five suites executed. APK signature verification with SDK 35's `apksigner`
succeeded (v2 signature). The APK SHA-256 remains identical to the original
unchanged-source APK above. The package metadata confirms API 26 minimum and
API 35 target; no installation or launch was performed.

## Lint findings retained for acquisition work

`:app:lintDebug` reports **2 errors, 65 warnings, and 1 hint**. The two errors are
unguarded API 28 calls in `CellularScanner.kt:252` and `:253` to
`CellIdentityLte.mccString` and `mncString`, while the app supports API 26.
These are existing application defects, recorded separately from test-harness
repairs. No lint baseline, suppression, SDK increase, or production-code change
was used to hide them. Address them in the acquisition-correctness milestone.

## Preserved evidence

- [Exact upstream build log](baseline-upstream.log).
- [First runnable test harness and lint log](baseline-harness-first-run.log).
- [Original lint findings](baseline-lint.txt).
- [Successful final build/test log](baseline-final.log).
- [Machine-readable results and APK hash](baseline-results.json).

Generated reports are also available under
`app/build/reports/tests/testDebugUnitTest/index.html` and
`app/build/reports/lint-results-debug.html`. Build outputs are ignored by Git.

## Device gate

`adb devices -l` returned no devices. The user explicitly deferred device testing
for now. The Pixel 9 Pro XL's installed Android
release, API level, build, and patch level remain **unconfirmed**. No APK has been
installed or run and no device tests have been performed.

Before device testing, connect and authorize the Pixel and record at least:

```sh
adb devices -l
adb -s DEVICE_SERIAL shell getprop ro.product.model
adb -s DEVICE_SERIAL shell getprop ro.build.version.release
adb -s DEVICE_SERIAL shell getprop ro.build.version.sdk
adb -s DEVICE_SERIAL shell getprop ro.build.fingerprint
adb -s DEVICE_SERIAL shell getprop ro.build.version.security_patch
```

Use the platform-tools binary under the SDK path above if `adb` is not on PATH.
Do not infer the installed OS from the phone model, an emulator, or the host SDK.
