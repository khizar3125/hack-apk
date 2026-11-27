package com.paradox.game

import com.paradox.game.data.SaveData
import com.paradox.game.app.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveProgressTest {

    @Test
    fun starProgressOnlyImproves() {
        val d = SaveData()
        d.markComplete("w1l1", 2, 9)
        assertEquals(2, d.progress["w1l1"])
        d.markComplete("w1l1", 1, 12)
        assertEquals(2, d.progress["w1l1"])
        d.markComplete("w1l1", 3, 8)
        assertEquals(3, d.progress["w1l1"])
        assertEquals(3, d.starsTotal())
        assertEquals(1, d.completedCount())
        assertEquals(8, d.bestMoves["w1l1"])
    }

    @Test
    fun dailyStreakCountsConsecutiveDays() {
        val d = SaveData()
        d.dailyLastDay = 100
        d.stats.dailyDone.add(100)
        d.stats.dailyDone.add(99)
        d.stats.dailyDone.add(98)
        Streak.compute(d)
        assertEquals(3, d.stats.dailyStreakBest)
    }

    @Test
    fun brokenStreakStopsAtGap() {
        val d = SaveData()
        d.dailyLastDay = 100
        d.stats.dailyDone.add(100)
        d.stats.dailyDone.add(97)
        Streak.compute(d)
        assertEquals(1, d.stats.dailyStreakBest)
    }
}
