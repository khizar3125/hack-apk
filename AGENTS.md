# Project notes

- Android app (Kotlin + C++ NDK). Build with `./gradlew assembleDebug` — the Gradle wrapper is committed.
- CI: `.github/workflows/build.yml` builds debug + release APKs on push and uploads them as artifacts.
- Runtime requires root (`su`) to read game memory and inject input. Use a rooted device/emulator to actually run scripts.
- This sandbox cannot run Gradle (JVM blocked) — never attempt a local build here.
- `app/src/main/assets/games.json` offsets are placeholders; edit the JSON, no recompile needed. `fix_games_json.py` converts hex literals to decimal if ever needed.
- UI palette lives in `res/values/colors.xml`; list item layouts are `res/layout/item_game.xml` and `item_script.xml`.
- `local.properties` is machine-specific and gitignored; CI sets `ANDROID_HOME` itself.
