# Online features (optional) — PARADOX

The game is 100% offline by default and requires no configuration. All online
functionality is isolated behind one interface:

`app/src/main/java/com/paradox/game/online/OnlineService.kt`

```kotlin
interface OnlineService {
    fun isOnline(): Boolean
    fun submitDailyScore(dayIndex: Long, score: Int)
    fun fetchDailyLeaderboard(dayIndex: Long): List<LeaderboardEntry>
    fun publishLevel(levelJson: String)
    fun fetchSharedLevels(): List<String>
}
```

`LocalOnlineService` is the current implementation — it keeps daily scores and
published levels in memory, so nothing leaves the device.

## Wiring a real backend later

1. Implement the interface (e.g. `RestOnlineService`) with your backend of
   choice (Firebase Functions + Firestore, Supabase, or a tiny REST service).
2. Levels transfer as the JSON string produced by
   `com.paradox.game.game.LevelCodec.encode(spec)` and are decoded with
   `LevelCodec.decode(json)` — decode is defensive and returns null on
   malformed input. Because a received level can be re-checked on device with
   `Solver.isSolvable(spec)`, shared levels can be verified before play.
3. `dayIndex` is a UTC day number; the Daily Challenge seed is
   `LevelGenerator.dailySeed(dayIndex)`, so a backend only needs to
   distribute the day index and every client generates the identical puzzle
   offline.
4. Swap the implementation at the single construction site in `MainActivity`.

Daily scores are already recorded locally on completion (`GameApp.onWin()`,
`Mode.DAILY` branch), so enabling connectivity never requires touching game
logic.
