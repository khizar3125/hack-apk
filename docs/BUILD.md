# Build instructions — PARADOX

## Toolchain

- JDK 17 (Temurin tested)
- Android SDK: platform `android-34`, build-tools `34.0.0`, platform-tools
- Gradle 8.7 (wrapper included — `./gradlew`)

## Setup

```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
yes | sdkmanager --licenses
echo "sdk.dir=/absolute/path/to/android-sdk" > local.properties
export JAVA_HOME=/path/to/jdk17
```

## Commands

```bash
./gradlew assembleDebug        # debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # release APK -> app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # solver/level/save unit tests
```

The release build type signs with the debug keystore so the APK installs on
any device without extra configuration. For Play Store publishing, replace
`signingConfig = signingConfigs.getByName("debug")` in `app/build.gradle.kts`
with your own keystore.

## Install on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Prebuilt copies are also committed under `dist/`.

## Performance notes

- Renders with OpenGL ES 2.0; bloom can be disabled in Settings (removes all
  FBO passes and particle spawning).
- Fixed geometry is baked into merged meshes per level; per-frame draws are
  limited to entity cubes and up to 900 particles.
- Solver runs on background threads (level load and creative-test) so the UI
  never blocks. UI overlay renders at display refresh with invalidation.
