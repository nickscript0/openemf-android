# Project instructions

Read [docs/PLAN.md](docs/PLAN.md) for the agreed scope, ordered implementation,
repository setup, device requirements, and validation gates. Establish and
document the unchanged OpenEMF build/tests before changing functionality. Confirm
the Pixel's installed Android version before device testing. Keep ElectroSmart
read-only and preserve attribution when adapting code. Keep evidence and progress
in the project documentation; do not imply unperformed device validation.

Prefer current stable, compatible tools and dependencies. Keep toolchain upgrades
as separate changes; the user explicitly prefers upgrades over old-version workarounds.

Target the user's Pixel on Android 17 (minimum API 37). Do not add older-Android
compatibility work. ADB confirmed Pixel 9 Pro XL, Android 17/API 37, build
CP3A.261005.005 before installation on 2026-10-23. See docs/DEVICE_VALIDATION.md for performed checks
and remaining physical validation; do not generalize a short USB-powered test.
