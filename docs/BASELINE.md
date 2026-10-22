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

To verify that application sources and build configuration remain unchanged:

```sh
git diff --exit-code 253731a021c27ec0df3b4c97e74096ce9d38b5b8 -- \
  app build.gradle.kts settings.gradle.kts gradle.properties gradle gradlew gradlew.bat
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
executable bit is repaired separately. The wrapper was not edited for this run.

## Reproduce the baseline

Run from the repository root:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ANDROID_HOME=/Users/nsvarich/Library/Android/sdk \
  /bin/sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  --no-daemon --console=plain
```

Initial console output is captured in
`/private/tmp/openemf-baseline-20261022.log`. Results below will distinguish
successful tasks, pre-existing failures, and unperformed device checks.

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

These failures are recorded before any test repair. Any subsequent repair will
be limited to the test harness, and its results will be reported separately
from this exact upstream run. Application behavior and SDK/toolchain versions
remain unchanged.

## Device gate

`adb devices -l` returned no devices. The Pixel 9 Pro XL's installed Android
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
