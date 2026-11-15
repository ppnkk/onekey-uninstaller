#!/usr/bin/env bash
# Builds OneKeyUninstaller.apk without Gradle (aapt2 + javac + d8 + apksigner).
#
# Requirements:
#   - JDK 17 on PATH (java, javac, keytool, jar).
#   - Android SDK with build-tools;34.0.0 and platforms;android-34.
# Set ANDROID_SDK (or ANDROID_HOME / ANDROID_SDK_ROOT) to your SDK path, then run:  ./build.sh
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "Android SDK not found. Set ANDROID_SDK to your SDK path." >&2
  exit 1
fi

BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
OUT="$(mktemp -d)"
KS="${ONEKEY_KEYSTORE:-$PROJ/onekey-release.jks}"
trap 'rm -rf "$OUT"' EXIT

mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/test"
LIBS="$PROJ/libs/api/classes.jar:$PROJ/libs/provider/classes.jar:$PROJ/libs/shared/classes.jar:$PROJ/libs/aidl/classes.jar"

echo "== resources"
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest "$PROJ/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 2 --version-name 1.1 "$OUT/res.zip"

echo "== compile"
find "$PROJ/src" "$OUT/gen" -name '*.java' > "$OUT/srcs.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -cp "$LIBS" -d "$OUT/classes" @"$OUT/srcs.txt"

echo "== unit test (encryption and backup format)"
javac -nowarn -encoding UTF-8 -cp "$OUT/classes" -d "$OUT/test" "$PROJ/test/CryptoTest.java"
java -cp "$OUT/classes:$OUT/test" CryptoTest

echo "== dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 \
  --lib "$JAR" --output "$OUT/dex" @"$OUT/classes.txt" \
  "$PROJ/libs/api/classes.jar" "$PROJ/libs/provider/classes.jar" \
  "$PROJ/libs/shared/classes.jar" "$PROJ/libs/aidl/classes.jar"

echo "== package"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
( cd "$OUT/dex" && jar uf "$OUT/unaligned.apk" classes.dex )
"$BT/zipalign" -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "== sign"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass onekey-store -keypass onekey-store \
    -alias onekey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=OneKey Uninstaller"
fi
java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass pass:onekey-store \
  --out "$PROJ/OneKeyUninstaller.apk" "$OUT/aligned.apk"

echo "== verify"
java -jar "$BT/lib/apksigner.jar" verify --verbose --min-sdk-version 26 "$PROJ/OneKeyUninstaller.apk"
rm -f "$PROJ/OneKeyUninstaller.apk.idsig"
echo "DONE: $PROJ/OneKeyUninstaller.apk"
