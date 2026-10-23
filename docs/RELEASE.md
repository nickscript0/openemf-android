# Local personal release

The personal release workflow builds and signs on the Mac, then installs through
USB/ADB. It does not publish to a store, register the app with Google, upload a
signing key, or introduce a cloud dependency. The current personal release is
**1.1.1 (version code 3)**, for Android 17/API 37 only. The first personal release
was 1.1.0 (version code 2); its original validation below remains checkpoint
evidence.

## Build and sign

Use the existing Android Studio JBR and SDK; the signing script uses installed
SDK Build Tools 37.0.0. Set `JAVA_HOME` and `ANDROID_HOME` for the local machine.

```sh
python3 scripts/personal_release.py build
```

This builds the release variant with R8 minification and resource shrinking,
rejects debuggable APKs or a minimum API other than 37, signs with the external
private key, verifies signatures and 16 KB ZIP alignment, and writes:

```text
app/build/outputs/apk/personal/openemf-1.1.1-3.apk
app/build/outputs/apk/personal/openemf-1.1.1-3.json
app/build/outputs/apk/personal/openemf-1.1.1-3-signature.txt
app/build/outputs/apk/personal/openemf-1.1.1-3-mapping.txt
```

Keep the version-matched R8 mapping file with each APK for decoding any future
obfuscated crash reports.

Gradle's `assembleRelease` output is unsigned. Install the signed **personal**
artifact above. The Settings version reads generated build metadata instead of
a hard-coded string. Increment `versionCode` for each subsequent release and
set a matching human-readable `versionName` in `app/build.gradle.kts`.

Run the host checks before distributing a new personal build:

```sh
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintRelease :app:lintDebug
```

## Private signing material

The Mac's signing directory is outside this checkout:

```text
~/.local/share/openemf-android/signing/
  personal-release.p12
  keystore-password.txt
  personal-release.pem
  debug-to-personal.lineage
```

The directory is owner-only (0700), with owner-only files (0600). The PKCS#12 key
is RSA 3072, with a randomly generated password; the password is read from the
private file, never placed in a command argument or committed. Keep an encrypted
offline backup of the entire directory. Losing the key prevents normal future
updates. No off-machine backup has been made by the agent.

The script accepts `OPENEMF_SIGNING_DIR` or `--signing-dir` for an external
restored directory. It refuses initialization in a nonempty directory and
refuses a signing directory inside the repository. Private-key, password, and
lineage files must never be added to Git or shared with an APK.

For a completely fresh personal signing identity on another developer's machine:

```sh
python3 scripts/personal_release.py init-signing
```

That creates a different identity and cannot update this Pixel's installation.
Do not run initialization to replace or recover the already released key.

## Upgrade from the existing debug installation

The Pixel initially had a Mac-debug-signed APK. A dedicated personal key was
created after matching the installed APK certificate to the existing debug
certificate. `apksigner rotate` created a signed proof linking that certificate
to the personal key. The previous certificate retains installed-data and signature-permission
capabilities for this transition. Android requires the latter to retain the
existing AndroidX dynamic-receiver permission owned by this package. Shared-UID,
rollback, and authenticator capabilities are not granted to the previous key.

The release is signed with the personal key and includes the rotation lineage,
targeting API 37. No database migration, history reset, uninstall, or data restore
is part of the upgrade. Future builds reuse the personal key and lineage; they
do not require the Mac debug private key. Debug builds still use their original
key and are not the everyday update path after rotation.

Before the transition, the installed APK and app-private data were copied to a
local temporary recovery snapshot. It contained 20 captures, 731 observations,
and two stopped recording sessions; database integrity was `ok`. Raw snapshots
and exported identities stay outside Git. The post-upgrade CSV is compared
against these original rows to check preservation after `run-as` becomes
unavailable for the non-debuggable release.

References:

- [Android signing-key rotation](https://source.android.com/docs/security/features/apksigning/v3).
- [apksigner](https://developer.android.com/tools/apksigner).
- [ADB installation and developer verification](https://developer.android.com/developer-verification/guides/faq).

## Validation (2026-10-23)

- Host build: successful in 96 seconds. All 182 existing debug-variant host tests
  passed. Both release and debug lint reported zero errors, 67 warnings, one hint.
  Release-variant unit tests were not run; the optimized artifact was exercised
  on the Pixel instead. See [build log](release-build.log) and
  [structured results](release-validation.json).
- APK: 8,318,436 bytes, non-debuggable, minimum/target API 37. V3 signature and
  signing lineage verified with Build Tools 37; 16 KB ZIP alignment passed.
  [Signature report](release-signature.txt). SHA-256:
  `f753647ed0e5153625d23ca5119748c2be788f5b180149058136296f8216d8fd`.
- The initial rotation lacked signature-permission capability, so Android rejected
  the update with DUPLICATE_PERMISSION for the existing AndroidX dynamic-receiver
  permission. The installed app remained untouched. The lineage was corrected,
  keeping the same new private key; the subsequent in-place update succeeded.
- Pixel Android 17/API 37, build CP3A.261005.005 was reconfirmed before testing.
  Version 1.1.0/code 2 and the same app ID were verified after update. Location,
  Bluetooth, and notification grants remained. `run-as` correctly reports that
  the app is not debuggable. Installed APK bytes exactly matched the signed file.
- The optimized app saved one manual capture and one recording capture. Wi-Fi,
  Bluetooth, and cellular all reported UPDATED in each; cellular remained LTE.
  Foreground-service types were accepted, the notification showed one completed
  capture, Pause changed it to paused, and Stop removed the service. Recording
  remained stopped at the end of the checks.
- Settings displayed **1.1.0 (2)**. Export through the real document picker saved
  a new file to personal Downloads. It parsed as 68 columns and 803 observations.
  All **731 pre-upgrade observations across 20 captures** were present with **zero
  differences across all 68 exported fields** when compared to the recovery
  database. Two new release captures contributed 72 additional observations.
  This checks persisted identities, values, times, acquisition provenance, and
  session links without uploading raw data.
- No fatal AndroidRuntime exception appeared for the checked release process.
  No database migration, uninstall, data clear, or data restore was performed.

The private key, password, lineage, raw database copy, and exported CSV are local.
The public certificate fingerprints and validation counts above contain no radio
identities. No off-machine signing-key backup was created; keep a secure encrypted
backup of the private signing directory for future updates.

Interruption handling, unplugged overnight recording/battery testing, and actual
NR measurements remain deferred as described in [radio validation](RADIO_VALIDATION.md).

## Compact-list update: 1.1.1 (2026-10-23)

- Includes the compact live-radio layout with a small information footer;
  source identities, metrics, radio summaries, freshness, and out-of-range
  explanations remain visible. See [COMPACT_SIGNALS.md](COMPACT_SIGNALS.md).
- Confirmed Pixel 9 Pro XL, Android 17/API 37, build CP3A.261005.005 through USB
  before installation. Installed version was 1.1.0/code 2; recording was stopped.
- Required release build, 182 debug-variant host tests, and debug/release lint
  passed in 89 seconds. Both lint variants report zero errors, 67 warnings, and
  one hint. See [build log](compact-release-build.log) and
  [structured results](compact-release-validation.json).
- Reused the external personal signing key and lineage; V3 signature and 16 KB
  alignment passed. APK is 8,334,820 bytes, non-debuggable, optimized, and API 37
  only. [Signing log](compact-release-signing.log) and
  [signature report](compact-release-signature.txt). SHA-256:
  `60717d697ed75e6c9a8bddac0190730927aca7a0d4b0811a2c601dac272860a3`.
- `adb install -r` succeeded. Package metadata reports 1.1.1/code 3, retained
  original first-install time, and retained Location, Bluetooth, and notification
  grants. Read-back installed APK bytes match the signed artifact exactly.
- No uninstall, data clear, schema change, signing-key replacement, or migration
  was performed. Before installation, History displayed 24 captures and 189
  source identities. The phone was locked after installation, and the user
  explicitly skipped the post-update visual/history-count checks. No further
  phone interaction followed that request. No claim of post-update history-count
  verification or a new CSV field-by-field comparison is made.

Raw UI snapshots and copies of both installed APKs remain in a private local
temporary folder outside Git. The matching R8 mapping stays beside the release
artifact. Wireless pairing attempts did not establish a connection; this update
was installed through the user's subsequently connected USB cable.
