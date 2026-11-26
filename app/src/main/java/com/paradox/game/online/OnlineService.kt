package com.paradox.game.online

class LeaderboardEntry(val name: String, val score: Int)

interface OnlineService {
    fun isOnline(): Boolean
    fun submitDailyScore(dayIndex: Long, score: Int)
    fun fetchDailyLeaderboard(dayIndex: Long): List<LeaderboardEntry>
    fun publishLevel(levelJson: String)
    fun fetchSharedLevels(): List<String>
}

class LocalOnlineService : OnlineService {
    private val dailyScores = HashMap<Long, MutableList<LeaderboardEntry>>()
    private val shared = ArrayList<String>()

    override fun isOnline(): Boolean = false

    override fun submitDailyScore(dayIndex: Long, score: Int) {
        val list = dailyScores.getOrPut(dayIndex) { ArrayList() }
        list.removeAll { it.name == "You" }
        list.add(LeaderboardEntry("You", score))
    }

    override fun fetchDailyLeaderboard(dayIndex: Long): List<LeaderboardEntry> =
        dailyScores[dayIndex]?.sortedByDescending { it.score } ?: emptyList()

    override fun publishLevel(levelJson: String) {
        if (!shared.contains(levelJson)) shared.add(levelJson)
    }

    override fun fetchSharedLevels(): List<String> = shared.toList()
}
