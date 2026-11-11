# Project notes

- Android app (Kotlin + C++ NDK). Build with `./gradlew assembleDebug` — the Gradle wrapper is committed.
- CI: `.github/workflows/build.yml` builds debug + release APKs on push and uploads them as artifacts.
- Runtime requires root (`su`) to read game memory and inject input. Use a rooted device/emulator to actually run scripts.
- This sandbox cannot run Gradle (JVM blocked) — never attempt a local build here.
- `app/src/main/assets/games.json` offsets are placeholders; edit the JSON, no recompile needed. `fix_games_json.py` converts hex literals to decimal if ever needed.
- UI palette lives in `res/values/colors.xml`; list item layouts are `res/layout/item_game.xml` and `item_script.xml`.
- `local.properties` is machine-specific and gitignored; CI sets `ANDROID_HOME` itself.
- `FeatureController.kt` — singleton for per-feature ON/OFF state, persisted in SharedPreferences. Used by FloatingIconService toggles and ScriptExecutor feature loops.
- `FloatingIconService.kt` — overlay uses FLAG_NOT_FOCUSABLE so game keyboard/touch keep working. Panel has per-feature Switch toggles (aimbot, esp, fly, speed, auto-tap, auto-fire, auto-play, etc.).
- Non-root mode requires the user to enable AccessibilityService in system settings; MainActivity starts NonRootAccessibilityService when the switch is toggled.
- Memory features (aimbot, ESP, fly, speed, etc.) need real offsets in games.json — currently all 0 (placeholders). Dump the target game's .so per version to fill them.
