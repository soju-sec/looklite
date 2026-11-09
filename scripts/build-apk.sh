#!/usr/bin/env bash
set -euo pipefail

echo "============================================="
echo "   LookLite — Standalone Android APK Builder"
echo "============================================="

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

JAVA_HOME="/home/soju/.local/jdk-17"
ANDROID_SDK_ROOT="/home/soju/.local/android-sdk"
BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/34.0.0"
PLATFORM_JAR="$ANDROID_SDK_ROOT/platforms/android-34/android.jar"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$BUILD_TOOLS:$PATH"

BUILD_DIR="$PROJECT_ROOT/android/build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$PROJECT_ROOT/android/gen"

echo "[1/7] Bundling WebGL2 engine & presets..."
bun build web/app.js --outfile web/bundle.js --target browser
mkdir -p android/assets/luts
cp web/index.html web/bundle.js web/styles.css android/assets/
cp luts/*.cube android/assets/luts/

echo "[2/7] Compiling Android resources with AAPT2..."
"$BUILD_TOOLS/aapt2" compile --dir android/res -o "$BUILD_DIR/compiled_res.zip"

echo "[3/7] Linking package & generating R.java..."
"$BUILD_TOOLS/aapt2" link \
    -I "$PLATFORM_JAR" \
    "$BUILD_DIR/compiled_res.zip" \
    --manifest android/AndroidManifest.xml \
    -A android/assets \
    -o "$BUILD_DIR/looklite.unaligned.apk" \
    --java android/gen

echo "[4/7] Compiling Java sources with javac..."
"$JAVA_HOME/bin/javac" \
    --release 17 \
    -d "$BUILD_DIR/classes" \
    -cp "$PLATFORM_JAR" \
    android/gen/app/looklite/android/R.java \
    android/src/app/looklite/android/MainActivity.java

echo "[5/7] Converting bytecode to Dalvik DEX with D8..."
"$BUILD_TOOLS/d8" \
    --output "$BUILD_DIR/" \
    --lib "$PLATFORM_JAR" \
    "$BUILD_DIR/classes/app/looklite/android/"*.class

echo "[6/7] Adding classes.dex to APK & zipaligning..."
(cd "$BUILD_DIR" && "$JAVA_HOME/bin/jar" uf looklite.unaligned.apk classes.dex)
"$BUILD_TOOLS/zipalign" -v -p 4 "$BUILD_DIR/looklite.unaligned.apk" "$BUILD_DIR/looklite.aligned.apk" > /dev/null

echo "[7/7] Signing APK with apksigner..."
KEYSTORE="$PROJECT_ROOT/android/looklite.keystore"
if [ ! -f "$KEYSTORE" ]; then
    "$JAVA_HOME/bin/keytool" -genkey -v \
        -keystore "$KEYSTORE" \
        -alias looklite \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass looklite \
        -keypass looklite \
        -dname "CN=LookLite, OU=Mobile Imaging, O=LookLite, L=Jakarta, ST=DKI, C=ID"
fi

OUTPUT_APK="$PROJECT_ROOT/LookLite-release.apk"
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias looklite \
    --ks-pass pass:looklite \
    --key-pass pass:looklite \
    --out "$OUTPUT_APK" \
    "$BUILD_DIR/looklite.aligned.apk"

echo "============================================="
echo "   Verifying signed APK..."
"$BUILD_TOOLS/apksigner" verify --verbose "$OUTPUT_APK"
echo "============================================="
echo "BUILD SUCCESSFUL!"
ls -lh "$OUTPUT_APK"
echo "Target APK generated at: $OUTPUT_APK"
