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

## Device testing caveat

No emulator/device was available in the build environment, so touch handling,
audio playback and GL rendering were verified statically and via unit tests of
the underlying logic rather than on hardware. The renderer uses only widely
available GLES 2.0 features (FBOs, point sprites) and defensive GL state
management.
