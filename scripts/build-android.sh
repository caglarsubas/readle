#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
build_number="${1:?Supply a monotonically increasing UAT version code}"
[[ "$build_number" =~ ^[1-9][0-9]*$ ]] || { echo 'Version code must be a positive integer.' >&2; exit 1; }
signing_dir="${READLE_SIGNING_DIR:-$HOME/.local/share/readle}"
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"
if [[ ! -f "$signing_dir/uat.properties" ]]; then
  umask 077
  READLE_STORE_PASSWORD="$(openssl rand -hex 24)"
  export READLE_STORE_PASSWORD
  keytool -genkeypair -noprompt -keystore "$signing_dir/uat.jks" -storepass:env READLE_STORE_PASSWORD -keypass:env READLE_STORE_PASSWORD -alias readle-uat -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Readle UAT'
  printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=readle-uat\nkeyPassword=%s\n' "$signing_dir/uat.jks" "$READLE_STORE_PASSWORD" "$READLE_STORE_PASSWORD" > "$signing_dir/uat.properties"
  unset READLE_STORE_PASSWORD
fi
cd "$root/apps/mobile/android"
./gradlew assembleRelease -PreadleVersionCode="$build_number" -PreadleSigningFile="$signing_dir/uat.properties" --console=plain
mkdir -p "$root/output/android"
cp app/build/outputs/apk/release/app-release.apk "$root/output/android/readle-uat-$build_number.apk"
cd "$root/output/android"
shasum -a 256 "readle-uat-$build_number.apk" > "readle-uat-$build_number.apk.sha256"
echo "Built $root/output/android/readle-uat-$build_number.apk"
