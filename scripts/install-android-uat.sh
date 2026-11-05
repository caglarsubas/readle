#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
build_number="${1:-10}"
[[ "$build_number" =~ ^[1-9][0-9]*$ ]] || { echo 'Supply a positive build number.' >&2; exit 1; }
readle_adb="${READLE_ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
apk_dir="$root/output/android"
apk_name="readle-uat-$build_number.apk"

[[ -x "$readle_adb" ]] || { echo 'Set READLE_ADB to the Android platform-tools adb executable.' >&2; exit 1; }
[[ -f "$apk_dir/$apk_name" && -f "$apk_dir/$apk_name.sha256" ]] || {
  echo "Build $build_number and its checksum must be present in $apk_dir." >&2
  exit 1
}
(cd "$apk_dir" && shasum -a 256 -c "$apk_name.sha256")

if ! "$readle_adb" -d get-state >/dev/null 2>&1; then
  echo 'Connect one unlocked Android phone by USB, enable USB debugging, and accept its computer-authorization prompt.' >&2
  "$readle_adb" devices -l
  exit 1
fi

# -r updates an existing installation while retaining its app data.
"$readle_adb" -d install -r "$apk_dir/$apk_name"
phone_fixtures='/sdcard/Download/Readle-UAT'
"$readle_adb" -d shell mkdir -p "$phone_fixtures"
"$readle_adb" -d push "$root/tests/fixtures/readle-sample.pdf" "$phone_fixtures/"
"$readle_adb" -d push "$root/tests/fixtures/readle-sample.epub" "$phone_fixtures/"

fixture_temp="$(mktemp -d)"
trap 'rm -rf "$fixture_temp"' EXIT
cp "$root/tests/fixtures/readle-sample.pdf" "$fixture_temp/readle-sample-copy.pdf"
printf 'Intentionally invalid PDF for the Readle import-failure test.\n' > "$fixture_temp/readle-corrupt.pdf"
"$readle_adb" -d push "$fixture_temp/readle-sample-copy.pdf" "$phone_fixtures/"
"$readle_adb" -d push "$fixture_temp/readle-corrupt.pdf" "$phone_fixtures/"
"$readle_adb" -d shell am start -n com.readle.uat/com.readle.MainActivity
printf '\nBuild %s installed. UAT import files are in Downloads/Readle-UAT on the phone.\n' "$build_number"
