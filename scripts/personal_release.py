#!/usr/bin/env python3
"""Build a locally signed personal APK. Private signing material stays outside Git."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ALIAS = "openemf-personal"


def command(args, capture=False):
    return subprocess.run([str(a) for a in args], cwd=ROOT, check=True,
                          stdout=subprocess.PIPE if capture else None,
                          text=True if capture else None).stdout


def sdk_path():
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured:
        return Path(configured).expanduser().resolve()
    local = ROOT / "local.properties"
    if local.exists():
        for line in local.read_text().splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")).resolve()
    raise SystemExit("Set ANDROID_HOME to the installed Android SDK.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["init-signing", "build"])
    parser.add_argument("--signing-dir", type=Path,
                        default=Path(os.environ.get("OPENEMF_SIGNING_DIR",
                                                    Path.home() / ".local/share/openemf-android/signing")))
    parser.add_argument("--build-tools", default="37.0.0")
    parser.add_argument("--rotate-from-debug", type=Path,
                        help="One-time transition from the installed Mac debug key")
    parser.add_argument("--installed-apk", type=Path,
                        help="Read-back installed APK, required for a debug-key transition")
    args = parser.parse_args()
    os.umask(0o077)
    directory = args.signing_dir.expanduser().resolve()
    if directory.is_relative_to(ROOT):
        raise SystemExit("Signing directory must be outside the repository.")
    sdk = sdk_path()
    tools = sdk / "build-tools" / args.build_tools
    signer = tools / "apksigner"
    java = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
    keytool = java / "keytool" if java else shutil.which("keytool")
    if not signer.is_file() or not keytool:
        raise SystemExit("Install the selected SDK build tools and set JAVA_HOME to a compatible JDK.")
    key = directory / "personal-release.p12"
    password = directory / "keystore-password.txt"
    lineage = directory / "debug-to-personal.lineage"
    certificate = directory / "personal-release.pem"

    if args.action == "init-signing":
        if bool(args.rotate_from_debug) != bool(args.installed_apk):
            raise SystemExit("A transition requires both --rotate-from-debug and --installed-apk.")
        if directory.exists() and any(directory.iterdir()):
            raise SystemExit("Signing directory is not empty; existing keys will never be replaced.")
        if args.rotate_from_debug:
            verification = command([signer, "verify", "--print-certs", args.installed_apk], capture=True)
            old = subprocess.check_output([str(keytool), "-exportcert", "-keystore",
                                           str(args.rotate_from_debug), "-storepass", "android",
                                           "-alias", "androiddebugkey"])
            digest = hashlib.sha256(old).hexdigest()
            fingerprints = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", verification)
            if fingerprints != [digest]:
                raise SystemExit("Installed signer does not match the debug key; no key created.")
        directory.mkdir(parents=True, mode=0o700, exist_ok=True)
        directory.chmod(0o700)
        with password.open("x") as output:
            output.write(secrets.token_urlsafe(48) + "\n")
        command([keytool, "-genkeypair", "-keystore", key, "-storetype", "PKCS12",
                 "-storepass:file", password, "-alias", ALIAS, "-keyalg", "RSA",
                 "-keysize", "3072", "-validity", "10000", "-dname", "CN=OpenEMF Personal"])
        command([keytool, "-exportcert", "-rfc", "-keystore", key, "-storepass:file", password,
                 "-alias", ALIAS, "-file", certificate])
        if args.rotate_from_debug:
            command([signer, "rotate", "--out", lineage, "--old-signer", "--ks",
                     args.rotate_from_debug, "--ks-key-alias", "androiddebugkey", "--ks-pass",
                     "pass:android", "--set-installed-data", "true", "--set-shared-uid", "false",
                     "--set-permission", "true", "--set-rollback", "false", "--set-auth", "false",
                     "--new-signer", "--ks", key, "--ks-key-alias", ALIAS,
                     "--ks-pass", f"file:{password}"])
        for file in directory.iterdir():
            file.chmod(0o600)
        print(f"Signing material created in {directory}. Back up this entire directory securely.")
        return

    if args.rotate_from_debug or args.installed_apk:
        raise SystemExit("Rotation arguments are only for one-time init-signing.")
    if not all(p.is_file() for p in [key, password, certificate]):
        raise SystemExit("Signing material missing. Run init-signing once; never recreate a released key.")
    command([ROOT / "gradlew", ":app:assembleRelease", "--no-daemon", "--console=plain"])
    outputs = ROOT / "app/build/outputs/apk/release"
    metadata = json.loads((outputs / "output-metadata.json").read_text())
    element, = metadata["elements"]
    version, code = element["versionName"], element["versionCode"]
    if not re.fullmatch(r"[A-Za-z0-9._-]+", version):
        raise SystemExit("Version name cannot be used safely as a file name.")
    unsigned = outputs / element["outputFile"]
    badging = command([tools / "aapt", "dump", "badging", unsigned], capture=True)
    if "application-debuggable" in badging or "sdkVersion:'37'" not in badging:
        raise SystemExit("Refusing to sign: expected a non-debuggable APK with minimum API 37.")
    destination = ROOT / "app/build/outputs/apk/personal"
    destination.mkdir(parents=True, exist_ok=True)
    apk = destination / f"openemf-{version}-{code}.apk"
    signing = [signer, "sign", "--ks", key, "--ks-key-alias", ALIAS,
               "--ks-pass", f"file:{password}", "--v1-signing-enabled", "false",
               "--v2-signing-enabled", "false", "--v3-signing-enabled", "true",
               "--v4-signing-enabled", "false", "--debuggable-apk-permitted", "false",
               "--min-sdk-version", "37", "--rotation-min-sdk-version", "37"]
    if lineage.exists():
        signing += ["--lineage", lineage]
    command(signing + ["--out", apk, unsigned])
    verification = command([signer, "verify", "--verbose", "--print-certs", apk], capture=True)
    command([tools / "zipalign", "-c", "-P", "16", "4", apk])
    mapping = destination / f"openemf-{version}-{code}-mapping.txt"
    shutil.copyfile(ROOT / "app/build/outputs/mapping/release/mapping.txt", mapping)
    (destination / f"openemf-{version}-{code}-signature.txt").write_text(verification)
    result = {"application_id": metadata["applicationId"], "version_name": version,
              "version_code": code, "min_api": 37, "debuggable": False,
              "minified": True, "resource_shrinking": True, "build_tools": args.build_tools,
              "signing_lineage_included": lineage.exists(), "size_bytes": apk.stat().st_size,
              "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "mapping_sha256": hashlib.sha256(mapping.read_bytes()).hexdigest(),
              "apk": str(apk.relative_to(ROOT))}
    (destination / f"openemf-{version}-{code}.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
