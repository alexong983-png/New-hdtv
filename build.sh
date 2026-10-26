#!/usr/bin/env bash
set -euo pipefail
APK_IN=Mytv_2_4_1.apk
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
AJAR=$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)
echo "build-tools: $BT  android.jar: $AJAR"

curl -sL -o apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar

# 1) compile the password gate to a dex
rm -rf gate_out gate_dex && mkdir -p gate_out gate_dex
javac --release 8 -encoding UTF-8 -cp "$AJAR" -d gate_out patch/PasswordGate.java
"$BT/d8" --lib "$AJAR" --release --min-api 21 --output gate_dex $(find gate_out -name '*.class')

# 2) decode code only (no resources), hook HomeActivity, rebuild dex
rm -rf decoded && java -jar apktool.jar d -r -f "$APK_IN" -o decoded
python3 patch/patch.py hook decoded
java -jar apktool.jar b decoded -o rebuilt.apk

# 3) put new dex files into the original apk, align, sign
python3 patch/patch.py repack "$APK_IN" rebuilt.apk gate_dex/classes.dex unsigned.apk
"$BT/zipalign" -f -p 4 unsigned.apk aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Hdtv"
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out Mytv_patched.apk aligned.apk
"$BT/apksigner" verify Mytv_patched.apk && echo OK
