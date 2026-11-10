#!/bin/bash
# Build the Runner APK. Requires: JDK 17+, Android SDK, NDK, gradle.
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  echo "Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK path."
  exit 1
fi

if [ ! -f local.properties ]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

echo "[1/2] Building debug APK..."
./gradlew --no-daemon assembleDebug

echo "[2/2] APK location:"
ls -lh app/build/outputs/apk/debug/app-debug.apk
