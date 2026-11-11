#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-/workspace/android-tools}"
BIN_PATH="${CODEX_BINARY:-/workspace/downloads/codex-app-server-aarch64-unknown-linux-musl}"
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-30/android.jar"
BUILD_DIR="$ROOT_DIR/build"
test -f "$BIN_PATH"
test -f "$ANDROID_JAR"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/apk/lib/arm64-v8a" "$BUILD_DIR/apk/assets" "$BUILD_DIR/empty-res" "$ROOT_DIR/dist"
"$BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$ROOT_DIR/AndroidManifest.xml" -o "$BUILD_DIR/base.apk"
javac --release 8 -classpath "$ANDROID_JAR" -d "$BUILD_DIR/classes" "$ROOT_DIR/src/dev/codexboard/diagnostic/MainActivity.java"
"$BUILD_TOOLS/d8" --min-api 30 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes/dev/codexboard/diagnostic/"*.class
cp "$BIN_PATH" "$BUILD_DIR/apk/lib/arm64-v8a/libcodex.so"
cp "$BUILD_DIR/dex/classes.dex" "$BUILD_DIR/apk/classes.dex"
cp "$ROOT_DIR/LICENSE-CODEX" "$BUILD_DIR/apk/assets/LICENSE-CODEX.txt"
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unaligned.apk"
(cd "$BUILD_DIR/apk" && zip -q -r "$BUILD_DIR/unaligned.apk" classes.dex lib assets)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"
if [ ! -f "$ROOT_DIR/debug.keystore" ]; then
  keytool -genkeypair -keystore "$ROOT_DIR/debug.keystore" -storepass android -keypass android -alias diagnostic -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Codex Board Diagnostic' >/dev/null 2>&1
fi
"$BUILD_TOOLS/apksigner" sign --ks "$ROOT_DIR/debug.keystore" --ks-key-alias diagnostic --ks-pass pass:android --key-pass pass:android --out "$ROOT_DIR/dist/codex-board-diagnostic.apk" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$ROOT_DIR/dist/codex-board-diagnostic.apk"
sha256sum "$ROOT_DIR/dist/codex-board-diagnostic.apk"
