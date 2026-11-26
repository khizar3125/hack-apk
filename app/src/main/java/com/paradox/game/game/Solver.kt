package com.paradox.game.game
import com.paradox.game.engine.Vec3i

class SolverResult(
    val solvable: Boolean,
    val actions: List<Int>,
    val nodesVisited: Int
)

object Solver {
    const val MAX_NODES = 400_000

    fun solve(spec: LevelSpec, maxNodes: Int = MAX_NODES, requireMinActions: Int = 0): SolverResult {
        val logic = GameLogic(spec)
        val start = logic.initial()
        if (start.dead || start.finished) return SolverResult(start.finished, emptyList(), 1)

        val rotCount = spec.rotators.size
        val seen = HashSet<Long>()
        data class Node(val st: GameState, val path: MutableList<Int>)

        val queue = ArrayDeque<Node>()
        queue.addLast(Node(start, ArrayList()))
        seen.add(start.key(rotCount))
        var visited = 0

        while (queue.isNotEmpty()) {
            if (visited > maxNodes) return SolverResult(false, emptyList(), visited)
            val (st, path) = queue.removeFirst()
            visited++
            for (action in 0..4) {
                val ns = st.copy()
                val ok = if (action == 4) {
                    logic.togglePhase(ns, null)
                    true
                } else {
                    val dirs = logic.moveDirsFor(ns)
                    logic.tryMove(ns, dirs[action], null)
                }
                if (!ok) continue
                if (ns.dead) continue
                if (ns.finished) {
                    val full = ArrayList(path)
                    full.add(action)
                    return if (full.size < requireMinActions) SolverResult(false, full, visited)
                    else SolverResult(true, full, visited)
                }
                val key = ns.key(rotCount)
                if (seen.contains(key)) continue
                seen.add(key)
                val np = ArrayList(path)
                np.add(action)
                queue.addLast(Node(ns, np))
                if (queue.size > 300_000) return SolverResult(false, emptyList(), visited)
            }
        }
        return SolverResult(false, emptyList(), visited)
    }

    fun isSolvable(spec: LevelSpec): Boolean = solve(spec).solvable
}
