# PARADOX — Impossible Reality

A complete offline 3D puzzle game for Android built around impossible geometry,
perspective, gravity control, portals and optical illusion. Zero external game
assets: every mesh, shader, sound and level is generated procedurally in code.

## Features

- **5 game modes**
  - **Campaign** — 36 handcrafted levels across 6 worlds, each introducing new
    mechanics and remixing old ones.
  - **Daily Challenge** — a new solvable puzzle every day, seeded from the date.
  - **Endless Paradox** — infinite procedurally generated puzzles with
    difficulty that scales with your streak.
  - **Creative Mode** — a built-in level editor (floors, walls, portals,
    gravity pads, switches, doors, bridges, cubes). Levels are solver-verified
    before they can be saved, so every shared level is guaranteed completable.
  - **Challenge Mode** — special conditions: move-limit sprints, no-reset runs,
    timed doors, long anomaly chains.
- **Real mechanics, all wired to gameplay:** gravity pads (6 directions),
  bidirectional portals, phase-shifted illusion bridges, pushable cubes that
  settle under gravity, hold-plates with channel-linked doors, rising-edge
  rotator platforms, blinking timed gates, wall climbing.
- **Guaranteed-solvable levels** — a built-in BFS solver over the full game
  state (player, gravity, phase, cubes, rotators) validates every generated or
  handcrafted level in the unit-test suite and in Creative mode.
- **Custom renderer** — hand-written OpenGL ES 2.0 engine: bloom
  (bright-pass → separable Gaussian blur → tonemap composite via FBOs),
  particle point-sprites, fresnel rim lighting, distance fog, procedural
  geometry. No engine dependency, no AndroidX.
- **Procedural original audio** — synthesis on `AudioTrack`: ambient 4-voice
  drone pad loop plus movement/portal/gravity/phase/win/click SFX, all computed
  at runtime. Music/SFX volume sliders and mute.
- **Persistent save** — campaign progress, 3-star ratings, 21 achievements,
  settings, custom levels, full gameplay statistics (JSON in private storage).
- Animated main menu, world/level select, pause menu, completion screen with
  star scoring vs the solver's optimal line, hints, stats, help screen.

## Controls

| Input | Action |
|---|---|
| Swipe / arrow pad | Move (relative to camera) |
| Single-finger drag | Orbit camera |
| Two-finger pinch | Zoom |
| PHASE button | Shift the illusion phase (when the level has bridges) |
| RST | Restart level |
| ? | Show hint |

## Build

Requirements: JDK 17, Android SDK (platform 34, build-tools 34). See
`docs/BUILD.md` for the full toolchain setup and commands.

```bash
./gradlew assembleDebug      # installable debug APK
./gradlew assembleRelease    # installable (debug-signed) release APK
./gradlew testDebugUnitTest  # full solver/level/save test suite
```

APKs land in `app/build/outputs/apk/…` and prebuilt copies in `dist/`.

## Project structure

```
app/src/main/java/com/paradox/game/
  engine/    Math (Vec3/Vec3i/Mat4) + OpenGL ES 2.0 renderer, bloom pipeline
  game/      Pure-Kotlin simulation, BFS solver, generator, 36 campaign levels
  render/    Scene renderer: meshes, camera orbit, entities, particles
  ui/        Canvas UI layer (all screens) + creative editor
  audio/     Procedural AudioTrack synthesis
  data/      Save system (JSON)
  app/       GameApp controller, achievements, challenges
  online/    Optional online service interface + local fallback
app/src/test/  Solvability + solver-replay + save/streak unit tests
```

## Going online (optional)

The game is fully offline. `online/OnlineService.kt` defines the seam for
daily sync, leaderboard and level sharing; `docs/ONLINE.md` documents how to
wire a real backend later.

## License / credits

Original game, original code, original procedural art & audio. No AI APIs, no
third-party game engines, no external assets.
