#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Cleaning and building release APK..."
./gradlew clean assembleRelease --no-daemon

echo "==> Copying APK to ./JustGBA.apk"
cp app/build/outputs/apk/release/app-release.apk JustGBA.apk

echo "==> Done: JustGBA.apk ($(du -h JustGBA.apk | cut -f1))"

echo "==> Installing on device via adb..."
adb install -r JustGBA.apk
