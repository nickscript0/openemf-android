# Current stable Android build

2026-10-22. This is a separate change after the validated acquisition checkpoint
`ecdb6c1`. The user explicitly prefers current stable tooling and dependencies
over maintaining old-version workarounds. The unchanged baseline remains
documented in [BASELINE.md](BASELINE.md).

## Selected versions

Versions were checked against the official Gradle service, Google Maven, and
Maven Central metadata. Versions are pinned; no dynamic or prerelease versions
are requested.

| Component | Baseline | Modernized |
| --- | --- | --- |
| Gradle | 8.13 | 9.8.2 |
| Android Gradle Plugin | 8.13.2 | 9.4.1 |
| Kotlin / Compose compiler plugin | 2.0.21 | 2.4.21 |
| KSP | 2.0.21-1.0.28 | 2.3.12 |
| Compile / target Android API | 35 / 35 | 37 / 37 |
| Hilt | 2.53.1 | 2.60.1 |
| Room | 2.6.1 | 2.8.5 |
| Compose BOM | 2024.12.01 | 2026.09.00 |
| Core KTX | 1.15.0 | 1.19.1 |
| Lifecycle | 2.8.7 | 2.11.0 |
| Activity Compose | 1.9.3 | 1.13.0 |
| Navigation Compose | 2.8.5 | 2.10.2 |
| AndroidX Hilt ViewModel Compose | 1.2.0 | 1.4.0 |
| DataStore | 1.1.1 | 1.3.0 |
| Play Services Location | 21.3.0 | 21.4.0 |
| Coroutines Play Services | 1.7.3 | 1.11.0 |
| OSMDroid | 6.1.18 | 6.1.20 |
| AndroidX instrumented-test JUnit extension | 1.2.1 | 1.3.0 |

The installed Android Studio runtime is now JBR 25.0.3; the old Gradle setup
failed before configuration on it. The modernized build uses that runtime.
AGP selects SDK Build Tools 36.0.0, which it installed using the already
accepted local SDK licenses. API 37 was already installed on the host. The
minimum supported Android API remains 26, and Java/Kotlin bytecode targets 17.
These build settings do not establish which Android version is on the Pixel.

## Migration

- Adopt AGP built-in Kotlin and its current DSL; remove the module's legacy
  `kotlin-android` plugin and `android.kotlinOptions` block. Pin Kotlin on the
  root plugin classpath so Kotlin and the Compose compiler use the same version.
- Keep Kotlin, Compose, Hilt, and Room. Keep the Room schema at version 1; this
  change does not add observation tables or discard existing data.
- Use AndroidX Hilt's current ViewModel Compose artifact/import.
- Regenerate the wrapper JAR and Unix/Windows scripts with Gradle 9.8.2. Pin the
  distribution SHA-256, fetched from the official Gradle service:
  `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`.
- Declare wrapper line endings explicitly for Unix and Windows.
- Do not opt out of built-in Kotlin or the new Android DSL to retain outdated
  plugin behavior. Do not suppress lint errors to pass the migration.

The SDK upgrade opts the APK into Android 17 target behaviors. Hardware scans,
permission dialogs, UI behavior, and compatibility on the user's actual phone
still need validation. No device installation/testing is performed in this run.

## Validation

Run from the repository root with the installed Android Studio runtime:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ANDROID_HOME=/Users/nsvarich/Library/Android/sdk \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  --continue --no-daemon --console=plain --warning-mode all
```

The first modernized invocation also ran `wrapper` to regenerate the bootstrap
files and completed all 60 tasks successfully in 2 minutes 49 seconds. **All 123
unit tests passed, with zero failures, errors, or skips. Lint reports zero
errors, 43 warnings, and one hint.** No lint suppressions or legacy toolchain
opt-outs were added. After removing two redundant `Unit` expressions flagged by
the new compiler, the command above checked the final sources and regenerated
wrapper again successfully in 42 seconds: 10 tasks executed and 49 up to date,
including reuse of the successful complete unit-test run. Counts above were
also checked against the generated JUnit and lint XML reports.

The debug APK's v2 signature verifies. APK metadata confirms package
`com.openemf.app`, version `1.0.0` (code 1), minimum API 26, and target/compile
API 37. No physical-device, emulator, UI, radio, or database migration tests were
performed. Room's schema declaration is unchanged; compatibility with existing
installed data has not been exercised. At this checkpoint an explicit database
migration was planned for the history milestone. The user later cancelled that
requirement and assumed fresh installations for all users; see
[HISTORY.md](HISTORY.md). That change removes the inherited destructive fallback
and creates a new first-install schema directly.

Remaining warnings include inherited UI deprecations and Room's deprecated
destructive-migration API. Gradle also reports deprecated configuration
visibility use during plugin configuration, scheduled for removal in Gradle 11.
The project does not call that API; the current build passes without suppressing
the warning.

Preserved evidence:

- [First complete upgrade build](toolchain-upgrade-build.log), including wrapper
  generation and all tests/lint tasks.
- [Final incremental verification](toolchain-build.log), using the regenerated
  wrapper and `--warning-mode all`.
- [Test/lint counts and APK fingerprint](toolchain-validation.json).
- [Official version-metadata sources checked on this date](toolchain-version-sources.json).

Generated APKs use the same output path on each build; milestone hashes identify
the artifact produced at that checkpoint, and later builds replace that file.

Official references:

- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes).
- [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin).
- [Kotlin/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
- [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk).
- [Gradle release metadata](https://services.gradle.org/versions/current).
