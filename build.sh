#!/usr/bin/env bash
# 本机（Ubuntu arm64 / proot）可直接跑的构建脚本。
# 依赖：openjdk-17、aapt、zipalign（apt），以及 android.jar、d8.jar、apksigner.jar。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${SDK:-/tmp/sdk/x}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/android-34/android.jar}"
D8_JAR="${D8_JAR:-$SDK/android-14/lib/d8.jar}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$SDK/android-14/lib/apksigner.jar}"
JAVA_BIN="${JAVA_BIN:-/usr/lib/jvm/java-17-openjdk-arm64/bin}"

export PATH="$JAVA_BIN:$PATH"
BUILD="$ROOT/build"
OUT="$ROOT/CxHelper.apk"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/gen"

echo "[1/5] aapt: manifest + resources + assets"
aapt package -f \
  -M "$ROOT/app/src/main/AndroidManifest.xml" \
  -S "$ROOT/app/src/main/res" \
  -A "$ROOT/app/src/main/assets" \
  -I "$ANDROID_JAR" \
  -F "$BUILD/unsigned.apk"

echo "[2/5] 生成 Js.java + javac"
python3 "$ROOT/gen_js.py"
find "$ROOT/app/src/main/java" "$ROOT/stubs" -name '*.java' > "$BUILD/sources.txt"
if ! javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" > "$BUILD/javac.log" 2>&1; then
  cat "$BUILD/javac.log"
  exit 1
fi
grep -v 'bootstrap class path' "$BUILD/javac.log" || true

# 编译桩只用于 javac，绝不能进 APK
rm -rf "$BUILD/classes/de"

echo "[3/5] d8"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
java -cp "$D8_JAR" com.android.tools.r8.D8 \
  --release --min-api 23 --lib "$ANDROID_JAR" \
  --output "$BUILD/dex" @"$BUILD/classes.txt"

echo "[4/5] 写入 classes.dex"
python3 - "$BUILD/unsigned.apk" "$BUILD/dex/classes.dex" <<'PY'
import shutil, sys, zipfile, os
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
with zipfile.ZipFile(apk, 'r') as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == 'classes.dex':
            continue
        zout.writestr(item, zin.read(item.filename))
    zout.write(dex, 'classes.dex')
os.replace(tmp, apk)
PY

echo "[5/5] zipalign + apksigner"
KS="$ROOT/build/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -storepass android \
    -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

zipalign -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
java -jar "$APKSIGNER_JAR" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT" "$BUILD/aligned.apk"
java -jar "$APKSIGNER_JAR" verify --print-certs "$OUT" | head -5

echo
echo "OK -> $OUT"
ls -lh "$OUT"