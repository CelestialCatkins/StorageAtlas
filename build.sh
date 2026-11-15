#!/usr/bin/env bash
# 存储星图 StorageAtlas · 纯命令行构建（无 Gradle）
#
# 依赖：JDK 17+、Android SDK（platforms/android-36、build-tools 任意带 d8/apksigner 的版本）
# 用法：./build.sh
# 可选环境变量：
#   ANDROID_HOME / ANDROID_SDK_ROOT   SDK 根目录（默认 /root/android-build/sdk）
#   ATLAS_BUILD_TOOLS                 build-tools 目录（默认取 SDK 里版本最高的一支）
#   ATLAS_PLATFORM                    platforms 下要用的 API 版本（默认 36）
#   ATLAS_HOST_TOOLS                  主机原生 aapt2/zipalign 所在目录（ARM64 上常需要）
#   VERSION_CODE / VERSION_NAME       版本号（默认 8 / 1.6.1）
#   ATLAS_KEYSTORE / ATLAS_KS_PASS    签名库与密码（默认 keystore.jks / storageatlas，缺失时自动生成）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/root/android-build/sdk}}"
API="${ATLAS_PLATFORM:-36}"
AJ="$SDK/platforms/android-$API/android.jar"
[ -f "$AJ" ] || { echo "缺少 $AJ（设 ANDROID_HOME 或 ATLAS_PLATFORM）" >&2; exit 1; }

if [ -z "${ATLAS_BUILD_TOOLS:-}" ]; then
  ATLAS_BUILD_TOOLS="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
fi
BT="${ATLAS_BUILD_TOOLS%/}"
[ -d "$BT" ] || { echo "找不到 build-tools（设 ATLAS_BUILD_TOOLS）" >&2; exit 1; }

# 手机上的 Android SDK 里，aapt2 / zipalign 是 x86_64 二进制，ARM64 主机跑不起来，
# 用 ARM64 版覆盖（lzhiyong/android-sdk-tools）。
HOST_TOOLS="${ATLAS_HOST_TOOLS:-/root/arm64-tools/build-tools}"
pick() {
  if [ -x "$HOST_TOOLS/$1" ]; then echo "$HOST_TOOLS/$1"; else echo "$BT/$1"; fi
}
AAPT2="$(pick aapt2)"
ZIPALIGN="$(pick zipalign)"
APKSIGNER="$BT/apksigner"

if ! command -v javac >/dev/null 2>&1; then
  for j in "${JAVA_HOME:-}" /usr/lib/jvm/*; do
    if [ -x "$j/bin/javac" ]; then JAVA_HOME="$j"; break; fi
  done
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi

VERSION_CODE="${VERSION_CODE:-8}"
VERSION_NAME="${VERSION_NAME:-1.6.1}"
KS="${ATLAS_KEYSTORE:-keystore.jks}"
KS_PASS="${ATLAS_KS_PASS:-storageatlas}"

echo "==> aapt2 compile"
rm -rf build gen && mkdir -p build/res build/classes build/dex gen
"$AAPT2" compile --dir res -o build/res.zip

echo "==> aapt2 link"
"$AAPT2" link -o build/base.apk \
  -I "$AJ" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  --java gen \
  --min-sdk-version 26 --target-sdk-version 35 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "==> javac"
find src gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -classpath "$AJ" \
  -d build/classes @build/sources.txt

echo "==> d8"
"$BT/d8" --release --lib "$AJ" --min-api 26 \
  --output build/dex $(find build/classes -name '*.class')

echo "==> package"
python3 - <<'PY'
import zipfile, shutil, os
shutil.copy('build/base.apk', 'build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex','classes.dex')
    for root,_,files in os.walk('assets'):
        for f in files:
            p=os.path.join(root,f)
            z.write(p, p)
PY

echo "==> align + sign"
"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f "$KS" ]; then
  echo "    生成签名库 $KS（仅本地开发用，勿提交）"
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias atlas -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Storage Atlas, OU=DSH, O=DSH, L=CN, S=CN, C=CN" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out build/storage-atlas.apk build/aligned.apk

ls -lh build/storage-atlas.apk
