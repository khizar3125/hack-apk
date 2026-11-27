# Testing report — PARADOX

## Automated test suite (`./gradlew testDebugUnitTest`)

8 JVM unit tests, all passing at delivery:

| Test | What it verifies |
|---|---|
| `SolverValidationTest.allCampaignLevelsAreSolvable` | every one of the 36 handcrafted campaign levels is solved end-to-end by the BFS solver |
| `SolverValidationTest.campaignParsAreFair` | stated PAR is never below the solver's optimal solution length |
| `SolverValidationTest.campaignSolverPathsReplayOnGameLogic` | every solver action path replays on the live `GameLogic` and reaches the goal without deaths — solver and gameplay use identical rules |
| `SolverValidationTest.generatorProducesSolvableLevels` | procedural generator (several seeds/difficulties) returns levels solved by the independent solver |
| `SaveProgressTest.starProgressOnlyImproves` | star records only improve |
| `SaveProgressTest.dailyStreakCountsConsecutiveDays` | daily streak math |
| `SaveProgressTest.brokenStreakStopsAtGap` | streak stops at gaps |
| `SmokeTest` | engine/logic smoke build |

## Mechanics verified through the solver replay

- Gravity pads switch down-direction in all 6 axes; falling resolution order
  (pad → fall → portal) verified by level w5l1/w5l6 which require exactly that.
- Portals teleport and cannot ping-pong (one teleport per resolve step).
- Pushable cubes settle with gravity, block movement, and activate plates.
- Hold plates + channel doors open only while occupied (player or cube).
- Rising-edge plates advance rotator platforms by 90° steps.
- Timed doors are closed outside their duty window.
- Phase bridges are solid only in their phase; phase toggle count validated.
- Death: falling into the void in any gravity orientation; falls > 48 cells
  manhattan kill (infinite-well guard).

## Build verification

- `testDebugUnitTest` — BUILD SUCCESSFUL, 8/8 tests.
- `assembleDebug` — `app/build/outputs/apk/debug/app-debug.apk` (≈1.0 MB).
- `assembleRelease` — `app/build/outputs/apk/release/app-release.apk` (≈0.75 MB).
- `apksigner verify` passes for both APKs (debug keystore so the release build
  is directly installable).
- `aapt dump badging` confirms `com.paradox.game` 1.0.0, minSdk 24,
  targetSdk 34, landscape, GL ES 2.0 required, correct icons.

## Design-level QA performed during development

- Two full solver-driven redesign passes; every campaign level geometry was
  debugged against BFS reachability (portal ping-pong, bridge phaseCount
  registration, wall climbing, cube support, par fairness).
- Bloom pipeline corrected from bright-pass-only to true separable Gaussian
  blur with dual-axis passes.
- Level generation has a 64-attempt validate-and-retry loop plus a solvable
  fallback so Daily/Endless can never ship an unsolvable or timed-out puzzle
  (hard 150k-node solver cap in generation, 400k in QA).
- Creative mode gating: SAVE is refused unless `lastTestOk` is set from an
  actual solver run of the current draft.
- Lifecycle: save() on pause, GL context + AudioTrack pause/resume handled,
  immersive mode re-applied on focus, singleTask launch with orientation
  configChanges so rotation does not recreate the activity.

## v1.1.0 graphics & onboarding pass (this release)

- Full visual rework of every 3D element: beveled floor tiles with emissive
  neon seams, glowing wall caps and accent lines, detailed player robot
  (body, visor, twin thrusters, antenna beacon) with walk squash-stretch and
  hover bob, rotating holographic octahedron + light beam on the goal,
  portal rings (10-segment) with orbiting particles, gravity pads with
  chevron arrows pointing in the new DOWN direction (rotated per axis),
  pedestal switches with animating press plates, sliding animated doors,
  blinking timed gates, rotating platform rotators, and two huge slow
  rotating background octahedra plus ambient dust particles.
- Camera: cinematic intro sweep on every level load, death camera shake,
  win shake + white flash.
- Onboarding (solve-it-yourself preserved - no auto-solve, no move scripts):
  - Level intro card with name, world, PAR and wrapped hint; TAP TO BEGIN.
  - Persistent first-move coach bar on the very first level.
  - One-time "NEW:" toasts the first time each mechanic appears
    (phase bridges, portals, gravity pads, cubes, timed gates).
  - Pulsing highlight on the PHASE button until first use.
  - HELP screen extended with a full color-coded legend of every entity
    and every HUD button; reachable from the main menu and the pause menu.
- Death overlay: TAP TO RETRY quick restart.
- HUD: run timer (mm:ss), move counter turns amber past PAR.
- Haptic feedback via Vibrator on push, portal, gravity, phase, death, win.

QA re-run after the pass: `compileDebugKotlin` clean, 8/8 unit tests pass,
debug + release APKs rebuilt, `apksigner verify` clean on both.

## v1.2.0 realism pass (this release)

- Lighting model upgraded in the fragment shader: Blinn-Phong specular
  highlights from a key light plus a violet fill light, third bounce light,
  height-based ambient occlusion, per-pixel gamma response.
- Procedural surface detail: world-space panel grid lines and hashed
  per-tile brightness variation on every walkable face (no textures needed).
- Contact shadows: dark flattened shadow plates render beneath the player,
  every pushable cube and the goal, grounding the objects visually.
- Cinematic post-processing (composite pass): vignette, subtle chromatic
  aberration towards screen edges, animated film grain, tonemapping.
- New persistence: per-level BEST move count (shown in level select and on
  the completion screen) and total playtime (shown in Settings).
- New save-test coverage for best-move recording.
- GitHub Actions workflow (.github/workflows/android.yml) builds, tests and
  uploads both APKs as artifacts on every push to main.

QA re-run: 8/8 unit tests, debug + release APKs rebuilt, apksigner verify
clean on both, aapt badging confirms v1.2.0 (versionCode 3).

Realism note (honest): v1.2.0 used a stylized neon-noir look achieved with
per-pixel lighting, AO, specular, shadows and post-processing - all
procedural, no image assets.

## v1.3.0 textured assets pass (this release)

- Self-generated PBR-style texture set (no downloads, license-clean):
  `tools/gen_textures.py` builds brushed-metal floor and wall panels and an
  amber crate texture - diffuse + Sobel-derived normal maps, with beveled
  borders, corner rivets, inset panels, scratches and grime. Generated PNGs
  live in `app/src/main/assets/tex/` (floor/wall 512², crate 256²) and are
  packed into the APK as verified assets.
- Engine upgrade: mesh vertex format extended to position + normal + UV +
  color (stride 44); new `uUseTex`/`uTexD`/`uTexN` uniforms; textured shader
  branch samples the diffuse map and perturbs the lighting normal via the
  normal map through an analytic tangent basis; mipmap + REPEAT sampling.
- Scene texturing: floor tiles, wall blocks and pushable crates are now
  texture-mapped with normal-mapped lighting; tint still carries the world
  palette so each world keeps its color identity.
- Environment: a ring of 18 tall wall-textured pillars with emissive caps
  now surrounds every level, adding depth and a sense of place behind the
  fog.
- Graceful fallback: if textures cannot load (or no Context is available),
  the engine binds a 1x1 white texture and `uUseTex=0`, reproducing the
  previous untextured look exactly.

QA re-run: 8/8 unit tests, debug + release APKs rebuilt (versionCode 4,
versionName 1.3.0), apksigner verify clean on both, `aapt dump badging`
confirms v1.3.0, `unzip -l` confirms all 6 textures inside both APKs.

## v1.3.1 launch-crash fix + bug-hunt pass

**Root cause of the app not opening (found and fixed):** the SOLID fragment
shader contained three GLSL ES 1.00 violations that made shader compilation
fail on device, which throws in `onSurfaceCreated` and crashes the app at
launch:

1. The ternary operator (`?:`) was used with vec3 operands - not allowed in
   GLSL ES 1.00; replaced with an explicit if/else tangent selection.
2. A vec3 literal was added onto a `float` accumulator (`spec += ... *
   vec3(...)`) - type error present since v1.2.0; `spec` is now a vec3.
3. `hash()` was called with a vec2 argument (no implicit conversion in ES
   1.00); the argument is now explicitly widened to vec3.

**Prevention:** `tools/check_shaders.py` extracts every shader from the
renderer and validates it with the Khronos glslang compiler targeting
`#version 100` (GLSL ES 1.00). It runs locally and as a CI step before every
build, so an invalid shader can never be merged again. All 7 shaders now
validate clean.

**Other fixes from the bug hunt:**

- `aUV` vertex attribute is now explicitly bound to location 3 at program
  link time (previously the linker could assign it elsewhere, silently
  breaking texture coordinates).
- Stale vertex-attrib state hygiene: attribute arrays 1-3 are disabled for
  fullscreen-quad and particle passes so no pass depends on leftover state.
- Launcher icon regenerated (see below) and adaptive-icon foreground sizes
  corrected to the proper 108dp bucket (108/162/216/324/432 px); the old
  generator also used nearest-neighbor scaling - the new one box-filters a
  3x-supersampled master.

**New launcher icon:** a true impossible (Penrose) triangle emblem - three
interwoven bars in cyan/violet/ice with two-tone 3D face shading, neon edge
highlights, soft bloom glow, on a dark blue radial-gradient background with
faint grid and stars. Generated by the rewritten `tools/gen_icons.py`
(supersampled, anti-aliased, all densities + adaptive foreground/round).

**Verification of the crash class:** shaders are compile-validated in CI;
8/8 unit tests pass; debug + release APKs rebuilt (versionCode 5,
versionName 1.3.1); apksigner verify clean on both; aapt badging confirms
v1.3.1; all 6 texture assets confirmed inside the APKs.

Realism note (honest, updated): the look is now physically-inspired textured
realism (albedo + normal maps, Blinn-Phong specular, AO, contact shadows,
bloom, tonemap, vignette, grain). It is real-time stylized realism on GLES
2.0 hardware - not photoreal CGI; that would require photogrammetry assets
and a PBR deferred renderer, which is out of scope for a zero-dependency,
offline APK of ~1 MB.

## Device testing caveat

No emulator/device was available in the build environment, so touch handling,
audio playback and GL rendering were verified statically and via unit tests of
the underlying logic rather than on hardware. The renderer uses only widely
available GLES 2.0 features (FBOs, point sprites) and defensive GL state
management.
