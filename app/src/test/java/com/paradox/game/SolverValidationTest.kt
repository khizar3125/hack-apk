package com.paradox.game

import com.paradox.game.game.CampaignLevels
import com.paradox.game.game.GameLogic
import com.paradox.game.game.LevelGenerator
import com.paradox.game.game.Solver
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SolverValidationTest {

    @Test
    fun allCampaignLevelsAreSolvable() {
        val failed = StringBuilder()
        for (spec in CampaignLevels.all()) {
            val res = Solver.solve(spec, maxNodes = 400_000)
            if (!res.solvable) {
                failed.append(spec.id).append(" (").append(spec.name).append(") UNSOLVABLE\n")
            }
        }
        assertTrue("Unsolvable levels found:\n$failed", failed.isEmpty())
    }

    @Test
    fun campaignSolverPathsReplayOnGameLogic() {
        for (spec in CampaignLevels.all()) {
            val res = Solver.solve(spec)
            if (!res.solvable) continue
            val logic = GameLogic(spec)
            for (a in res.actions) {
                logic.performAction(a, null)
                assertTrue("${spec.id}: died mid-solution", !logic.state.dead)
            }
            assertTrue("${spec.id}: replayed solution did not finish", logic.state.finished)
        }
    }

    @Test
    fun campaignParsAreFair() {
        val unfair = StringBuilder()
        for (spec in CampaignLevels.all()) {
            val res = Solver.solve(spec)
            if (res.solvable && res.actions.size > spec.par) {
                unfair.append("${spec.id}: par=${spec.par} solution=${res.actions.size}\n")
            }
        }
        println(unfair)
        assertTrue("Par values below optimal solutions:\n$unfair", unfair.isEmpty())
    }

    @Test
    fun generatorProducesSolvableLevels() {
        for (seed in 1L..8L) {
            for (d in intArrayOf(2, 5, 8)) {
                val spec = LevelGenerator.generate(LevelGenerator.dailySeed(seed), d)
                val res = Solver.solve(spec)
                assertTrue("generator seed=$seed d=$d unsolvable", res.solvable)
            }
        }
    }
}
