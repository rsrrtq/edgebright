#!/bin/zsh
# EdgeBright APK 构建脚本 (无 gradle, 直接 aapt2 + javac + d8 + apksigner)
set -e

ROOT="/Users/nixiaoxiao/WorkBuddy/mymtapk/edgebright"
SDK="/Users/nixiaoxiao/WorkBuddy/mymtapk/android-sdk"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

APP="$ROOT/app"
BUILD="$ROOT/build"
mkdir -p "$BUILD/classes" "$BUILD/apk"

echo "[1/6] aapt2 编译资源"
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "[2/6] aapt2 链接 (生成基础 APK + R.java)"
"$BT/aapt2" link \
  -o "$BUILD/apk/base.apk" \
  --manifest "$APP/AndroidManifest.xml" \
  -I "$PLATFORM" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 4 \
  --version-name 1.1.2 \
  --java "$BUILD/gen" \
  "$BUILD/res.zip"

echo "[3/6] javac 编译"
find "$APP/src" "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
rm -rf "$BUILD/classes"
mkdir -p "$BUILD/classes"
JAVAC_OUT=$("$JAVA_HOME/bin/javac" \
  -source 8 -target 8 \
  -bootclasspath "$PLATFORM:$BT/core-lambda-stubs.jar" \
  -encoding UTF-8 \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>&1)
JAVAC_STATUS=$?
if [ -n "$JAVAC_OUT" ]; then
  echo "$JAVAC_OUT"
fi
if [ $JAVAC_STATUS -ne 0 ]; then
  echo "javac 编译失败 (exit $JAVAC_STATUS), 终止构建"
  exit 1
fi

echo "[4/6] d8 生成 dex"
"$BT/d8" --release --lib "$PLATFORM" \
  --output "$BUILD/apk" \
  $(find "$BUILD/classes" -name "*.class")

echo "[5/6] 打包 dex 进 APK"
cd "$BUILD/apk"
zip -q -j base.apk classes.dex

echo "[6/6] 对齐 + 签名"
"$BT/zipalign" -f 4 base.apk aligned.apk
KS="$ROOT/signing.keystore"
if [ ! -f "$KS" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" \
    -alias edgebright -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass edgebright -keypass edgebright \
    -dname "CN=EdgeBright, OU=dev, O=edgebright, C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:edgebright \
  --key-pass pass:edgebright --out "$ROOT/EdgeBright.apk" aligned.apk
"$BT/apksigner" verify "$ROOT/EdgeBright.apk"

echo "完成: $ROOT/EdgeBright.apk"
