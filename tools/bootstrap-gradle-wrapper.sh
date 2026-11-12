#!/usr/bin/env bash
# Fetch the OFFICIAL Gradle 8.13 wrapper files from the pinned Gradle v8.13.0 release.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="https://raw.githubusercontent.com/gradle/gradle/v8.13.0"
mkdir -p "$ROOT/gradle/wrapper"
curl -fL "$BASE/gradlew" -o "$ROOT/gradlew"
curl -fL "$BASE/gradlew.bat" -o "$ROOT/gradlew.bat"
curl -fL "$BASE/gradle/wrapper/gradle-wrapper.jar" -o "$ROOT/gradle/wrapper/gradle-wrapper.jar"
chmod +x "$ROOT/gradlew"
echo 'Official Gradle wrapper downloaded. Sync in Android Studio.'
