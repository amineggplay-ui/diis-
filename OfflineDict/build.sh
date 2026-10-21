#!/usr/bin/env bash
# بناء APK كامل بأمر واحد:   ./build.sh
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT_VER="30.0.3"
PLAT="android-10"

if [ ! -d "$SDK" ]; then echo "لم أجد Android SDK. اضبط ANDROID_HOME"; exit 1; fi

SDKM="$(ls "$SDK"/cmdline-tools/*/bin/sdkmanager 2>/dev/null | head -1)"
if [ ! -d "$SDK/platforms/$PLAT" ] || [ ! -d "$SDK/build-tools/$BT_VER" ]; then
  yes | "$SDKM" --licenses >/dev/null 2>&1 || true
  "$SDKM" "platforms;$PLAT" "build-tools;$BT_VER"
fi

BT="$SDK/build-tools/$BT_VER"
JAR="$SDK/platforms/$PLAT/android.jar"

echo ">> بناء قاعدة البيانات"
python3 tools/make_db.py

rm -rf build && mkdir -p build/classes

echo ">> ترجمة جافا"
javac -source 8 -target 8 -nowarn -bootclasspath "$JAR" -d build/classes src/com/offlinedict/*.java

echo ">> تحويل إلى dex"
"$BT/d8" --release --min-api 10 --lib "$JAR" --output build $(find build/classes -name '*.class')

echo ">> تجميع الموارد (قاعدة البيانات بدون ضغط)"
"$BT/aapt" package -f -M AndroidManifest.xml -S res -A assets -I "$JAR" -0 db -F build/unsigned.apk
(cd build && "$BT/aapt" add unsigned.apk classes.dex >/dev/null)

echo ">> محاذاة وتوقيع"
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
[ -f build/debug.jks ] || keytool -genkeypair -keystore build/debug.jks -alias k \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android \
  -dname "CN=OfflineDict" >/dev/null 2>&1
"$BT/apksigner" sign --ks build/debug.jks --ks-pass pass:android --key-pass pass:android \
  --out OfflineDict.apk build/aligned.apk

echo ">> تم: $(pwd)/OfflineDict.apk  ($(du -h OfflineDict.apk | cut -f1))"
