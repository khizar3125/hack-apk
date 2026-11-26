package com.paradox.game.game
import com.paradox.game.engine.Vec3i

object CampaignLevels {

    private val cache = ArrayList<LevelSpec>()

    fun all(): List<LevelSpec> {
        if (cache.isEmpty()) buildAll(cache)
        return cache
    }

    fun byId(id: String): LevelSpec? = all().firstOrNull { it.id == id }

    val worldNames = listOf(
        "I - AWAKENING",
        "II - MASS & MACHINES",
        "III - LIGHT BETWEEN WORLDS",
        "IV - WORMHOLES",
        "V - GRAVITY WELLS",
        "VI - THE IMPOSSIBLE OBJECT"
    )

    fun levelsOfWorld(w: Int): List<LevelSpec> = all().filter { it.world == w }

    private fun lb(id: String, name: String, world: Int, tint: Int, hint: String) =
        LevelBuilder(id, name, world, tint, hint)

    private fun buildAll(out: ArrayList<LevelSpec>) {
        run {
            val b = lb("w1l1", "FIRST LIGHT", 0, 0, "Swipe to move. Reach the light.")
            b.slab(0, 8, -1, 0, 0)
            b.spawnOn(1, 0)
            b.goalOn(7, 0)
            b.par = 7
            out.add(b.build())
        }
        run {
            val b = lb("w1l2", "THE CREVASSE", 0, 0, "The void below is patient. Walk around it.")
            b.slab(0, 10, -1, 0, 2)
            b.removeAt(4, -1, 0)
            b.removeAt(4, -1, 1)
            b.spawnOn(1, 1)
            b.goalOn(9, 1)
            b.par = 10
            out.add(b.build())
        }
        run {
            val b = lb("w1l3", "CLIMB", 0, 0, "Some walls are one step tall. Walk onto them.")
            b.slab(0, 7, -1, 0, 0)
            b.block(3, 0, 0)
            b.block(5, 0, 0)
            b.spawnOn(0, 0)
            b.goalOn(7, 0)
            b.par = 9
            out.add(b.build())
        }
        run {
            val b = lb("w1l4", "THE GATE", 0, 0, "Step on the plate. The gate listens while you stand.")
            b.slab(0, 9, -1, 0, 0)
            b.slab(4, 6, -1, 1, 1)
            b.door(Vec3i(5, 0, 0), "g1")
            b.switch(Vec3i(5, 0, 1), "g1")
            b.spawnOn(1, 0)
            b.goalOn(8, 0)
            b.par = 11
            out.add(b.build())
        }
        run {
            val b = lb("w1l5", "TWO GATES", 0, 0, "Two gates, two plates. Stand, step, repeat.")
            b.slab(0, 9, -1, 0, 0)
            b.slab(3, 5, -1, 1, 1)
            b.slab(6, 8, -1, 1, 1)
            b.door(Vec3i(4, 0, 0), "g1")
            b.door(Vec3i(7, 0, 0), "g2")
            b.switch(Vec3i(4, 0, 1), "g1")
            b.switch(Vec3i(7, 0, 1), "g2")
            b.spawnOn(0, 0)
            b.goalOn(9, 0)
            b.par = 15
            out.add(b.build())
        }
        run {
            val b = lb("w1l6", "THE LONG WAY", 0, 0, "Take the long way around. It is the only way.")
            b.slab(0, 9, -1, 0, 0)
            b.slab(9, 9, -1, 1, 4)
            b.slab(0, 9, -1, 5, 5)
            b.slab(0, 0, -1, 1, 4)
            b.block(3, 0, 0); b.block(3, 1, 0)
            b.block(6, 0, 0); b.block(6, 1, 0)
            b.spawnOn(0, 0)
            b.goalOn(0, 5)
            b.par = 22
            out.add(b.build())
        }

        run {
            val b = lb("w2l1", "MASS", 1, 1, "Push the cube onto the plate. A cube keeps pressing.")
            b.slab(0, 9, -1, 0, 0)
            b.slab(5, 5, -1, -1, -2)
            b.slab(3, 4, -1, -2, -2)
            b.door(Vec3i(6, 0, 0), "g1")
            b.switch(Vec3i(3, 0, -2), "g1")
            b.cube(Vec3i(4, 0, -2))
            b.spawnOn(0, 0)
            b.goalOn(9, 0)
            b.par = 14
            out.add(b.build())
        }
        run {
            val b = lb("w2l2", "TURN THE WORLD", 1, 1, "Step on the plate. The floor listens and rotates.")
            b.slab(0, 6, -1, 0, 0)
            b.slab(0, 6, -1, 4, 4)
            b.rotator(Vec3i(3, -1, 2), listOf(Vec3i(2, -1, 2), Vec3i(3, -1, 2), Vec3i(4, -1, 2), Vec3i(3, -1, 1)), "g1")
            b.switch(Vec3i(1, 0, 0), "g1")
            b.spawnOn(0, 0)
            b.goalOn(5, 4)
            b.par = 14
            out.add(b.build())
        }
        run {
            val b = lb("w2l3", "ROTOR & GATE", 1, 1, "Rotation opens paths walking cannot. Then a gate asks for weight.")
            b.slab(0, 8, -1, 0, 0)
            b.slab(0, 8, -1, 4, 4)
            b.rotator(Vec3i(4, -1, 2), listOf(Vec3i(3, -1, 2), Vec3i(4, -1, 2), Vec3i(5, -1, 2), Vec3i(4, -1, 1)), "g1")
            b.switch(Vec3i(0, 0, 0), "g1")
            b.slab(4, 6, -1, -1, -1)
            b.cube(Vec3i(4, 0, -1))
            b.switch(Vec3i(6, 0, -1), "g2")
            b.door(Vec3i(6, 0, 4), "g2")
            b.spawnOn(0, 0)
            b.goalOn(8, 4)
            b.par = 20
            out.add(b.build())
        }
        run {
            val b = lb("w2l4", "CRANKED", 1, 1, "Turn, cross, turn again.")
            b.slab(0, 6, -1, 0, 0)
            b.slab(0, 6, -1, 4, 4)
            b.rotator(Vec3i(3, -1, 2), listOf(Vec3i(2, -1, 2), Vec3i(3, -1, 2), Vec3i(4, -1, 2), Vec3i(3, -1, 1)), "g1")
            b.switch(Vec3i(0, 0, 0), "g1")
            b.spawnOn(1, 0)
            b.goalOn(5, 4)
            b.par = 12
            out.add(b.build())
        }
        run {
            val b = lb("w2l5", "COUNTERWEIGHT", 1, 1, "Cube presses while you walk away. Use that.")
            b.slab(0, 10, -1, 0, 0)
            b.slab(2, 4, -1, 3, 5)
            b.slab(1, 1, -1, 1, 2)
            b.door(Vec3i(6, 0, 0), "g1")
            b.cube(Vec3i(3, 0, 4))
            b.switch(Vec3i(2, 0, 4), "g1")
            b.spawnOn(1, 0)
            b.goalOn(10, 0)
            b.par = 16
            out.add(b.build())
        }
        run {
            val b = lb("w2l6", "MACHINIST", 1, 1, "Two cubes. Two plates. One way through.")
            b.slab(0, 10, -1, 0, 0)
            b.slab(0, 3, -1, 4, 4)
            b.slab(7, 10, -1, 4, 4)
            b.slab(3, 3, -1, 1, 3)
            b.slab(7, 7, -1, 1, 3)
            b.door(Vec3i(4, 0, 0), "g1")
            b.door(Vec3i(6, 0, 0), "g2")
            b.cube(Vec3i(2, 0, 4))
            b.cube(Vec3i(9, 0, 4))
            b.switch(Vec3i(1, 0, 4), "g1")
            b.switch(Vec3i(8, 0, 4), "g2")
            b.spawnOn(0, 0)
            b.goalOn(10, 0)
            b.par = 22
            out.add(b.build())
        }

        run {
            val b = lb("w3l1", "WHICH IS REAL?", 2, 2, "Press the eye. Some floors only exist from the other side.")
            b.slab(0, 6, -1, 0, 0)
            b.slab(0, 6, -1, 3, 3)
            b.slab(3, 3, -1, 1, 1)
            b.bridgeAt(3, -1, 2, 1)
            b.spawnOn(1, 0)
            b.goalOn(5, 3)
            b.par = 11
            out.add(b.build())
        }
        run {
            val b = lb("w3l2", "TWO TRUTHS", 2, 2, "Phase twice. Neither truth is enough alone.")
            b.slab(0, 8, -1, 0, 0)
            b.slab(0, 8, -1, 2, 2)
            b.slab(0, 8, -1, 4, 4)
            b.bridgeAt(2, -1, 1, 1)
            b.bridgeAt(6, -1, 3, 0)
            b.spawnOn(0, 0)
            b.goalOn(8, 4)
            b.par = 16
            out.add(b.build())
        }
        run {
            val b = lb("w3l3", "SCHRODINGER", 2, 2, "The crossing and the gate share a secret.")
            b.slab(0, 9, -1, 0, 0)
            b.slab(0, 9, -1, 3, 5)
            b.slab(4, 4, -1, 1, 1)
            b.bridgeAt(4, -1, 2, 1)
            b.door(Vec3i(7, 0, 4), "g1")
            b.switch(Vec3i(1, 0, 4), "g1")
            b.spawnOn(0, 0)
            b.goalOn(9, 4)
            b.par = 16
            out.add(b.build())
        }
        run {
            val b = lb("w3l4", "FATA MORGANA", 2, 2, "Two crossings, opposite truths.")
            b.slab(0, 10, -1, 0, 0)
            b.slab(0, 10, -1, 3, 3)
            b.slab(3, 3, -1, 1, 2)
            b.bridgeAt(3, -1, 2, 1)
            b.slab(8, 8, -1, 1, 1)
            b.bridgeAt(8, -1, 2, 0)
            b.spawnOn(0, 0)
            b.goalOn(10, 3)
            b.par = 18
            out.add(b.build())
        }
        run {
            val b = lb("w3l5", "HALL OF MIRRORS", 2, 2, "The path flickers. Find the rhythm.")
            b.slab(0, 10, -1, 0, 0)
            b.slab(0, 10, -1, 2, 2)
            b.slab(0, 10, -1, 4, 4)
            b.bridgeAt(2, -1, 1, 1)
            b.bridgeAt(4, -1, 1, 0)
            b.bridgeAt(7, -1, 3, 1)
            b.slab(7, 7, -1, 1, 1)
            b.spawnOn(0, 0)
            b.goalOn(10, 4)
            b.par = 20
            out.add(b.build())
        }
        run {
            val b = lb("w3l6", "PERSPECTIVE", 2, 2, "The cube is heavy in every reality.")
            b.slab(0, 9, -1, 0, 0)
            b.slab(0, 9, -1, 3, 5)
            b.slab(0, 2, -1, -1, -1)
            b.slab(4, 4, -1, 1, 2)
            b.bridgeAt(4, -1, 2, 1)
            b.cube(Vec3i(0, 0, -1))
            b.switch(Vec3i(2, 0, -1), "g1")
            b.door(Vec3i(6, 0, 4), "g1")
            b.spawnOn(0, 0)
            b.goalOn(9, 4)
            b.par = 20
            out.add(b.build())
        }

        run {
            val b = lb("w4l1", "WORMHOLE", 3, 3, "Folded space is shorter. Step through.")
            b.slab(0, 5, -1, 0, 0)
            b.slab(7, 12, -1, 8, 8)
            b.portal(Vec3i(5, 0, 0), Vec3i(7, 0, 8))
            b.spawnOn(0, 0)
            b.goalOn(12, 8)
            b.par = 11
            out.add(b.build())
        }
        run {
            val b = lb("w4l2", "ROUND TRIP", 3, 3, "Two doors. Visit one to open the other's road.")
            b.slab(0, 9, -1, 0, 0)
            b.slab(0, 9, -1, 5, 5)
            b.slab(3, 5, -1, 2, 3)
            b.portal(Vec3i(8, 0, 0), Vec3i(1, 0, 5))
            b.portal(Vec3i(4, 0, 2), Vec3i(6, 0, 5))
            b.switch(Vec3i(3, 0, 3), "g1")
            b.door(Vec3i(2, 0, 5), "g1")
            b.spawnOn(0, 0)
            b.goalOn(9, 5)
            b.par = 18
            out.add(b.build())
        }
        run {
            val b = lb("w4l3", "WARP LATTICE", 3, 3, "Distance is an opinion.")
            b.slab(0, 4, -1, 0, 0)
            b.slab(7, 11, -1, 2, 2)
            b.slab(0, 7, -1, 6, 6)
            b.portal(Vec3i(4, 0, 0), Vec3i(11, 0, 2))
            b.portal(Vec3i(7, 0, 2), Vec3i(0, 0, 6))
            b.spawnOn(0, 0)
            b.goalOn(7, 6)
            b.par = 16
            out.add(b.build())
        }
        run {
            val b = lb("w4l4", "ECHO CHAMBER", 3, 3, "The cube echoes your intent across the chamber.")
            b.slab(0, 8, -1, 0, 0)
            b.slab(2, 6, -1, 3, 5)
            b.slab(4, 4, -1, 1, 2)
            b.portal(Vec3i(2, 0, 3), Vec3i(6, 0, 5))
            b.cube(Vec3i(3, 0, 4))
            b.switch(Vec3i(2, 0, 4), "g1")
            b.door(Vec3i(8, 0, 0), "g1")
            b.spawnOn(0, 0)
            b.goalOn(8, 0)
            b.par = 19
            out.add(b.build())
        }
        run {
            val b = lb("w4l5", "WHEEL OF AEONS", 3, 3, "Time turns. So do floors.")
            b.slab(0, 6, -1, 0, 0)
            b.slab(0, 6, -1, 8, 8)
            b.rotator(Vec3i(3, -1, 4), listOf(Vec3i(3, -1, 5), Vec3i(2, -1, 4), Vec3i(3, -1, 4), Vec3i(4, -1, 4)), "g1")
            b.portal(Vec3i(5, 0, 0), Vec3i(1, 0, 8))
            b.switch(Vec3i(2, 0, 0), "g1")
            b.spawnOn(0, 0)
            b.goalOn(6, 8)
            b.par = 16
            out.add(b.build())
        }
        run {
            val b = lb("w4l6", "THE CIRCLE CLOSES", 3, 3, "Bring the mass. Close the loop.")
            b.slab(0, 10, -1, 0, 0)
            b.slab(0, 10, -1, 6, 8)
            b.slab(10, 10, -1, 1, 5)
            b.portal(Vec3i(3, 0, 0), Vec3i(9, 0, 7))
            b.cube(Vec3i(1, 0, 7))
            b.switch(Vec3i(3, 0, 7), "g1")
            b.door(Vec3i(6, 0, 0), "g1")
            b.spawnOn(0, 0)
            b.goalOn(10, 0)
            b.par = 20
            out.add(b.build())
        }

        run {
            val b = lb("w5l1", "FALLING SIDEWAYS", 4, 4, "Down is a direction, not a destination.")
            b.slab(0, 6, -1, 0, 0)
            b.gravPad(Vec3i(6, 0, 0), 2)
            for (y in 0..2) for (z in 0..3) b.block(7, y, z)
            b.gravPad(Vec3i(6, 2, 2), 0)
            b.slab(3, 6, -1, 2, 2)
            b.goalOn(3, 2)
            b.spawnOn(0, 0)
            b.par = 14
            out.add(b.build())
        }
        run {
            val b = lb("w5l2", "LATERAL THINKING", 4, 4, "The floor ends. The wall begins.")
            b.slab(0, 5, -1, 0, 0)
            b.gravPad(Vec3i(5, 0, 0), 2)
            for (y in 0..2) for (z in 0..3) b.block(6, y, z)
            b.gravPad(Vec3i(5, 2, 3), 0)
            b.slab(2, 5, -1, 3, 3)
            b.goalOn(2, 3)
            b.spawnOn(0, 0)
            b.par = 14
            out.add(b.build())
        }
        run {
            val b = lb("w5l3", "GRAVITY GATE", 4, 4, "A cube holds the gate. Even sideways.")
            b.slab(0, 7, -1, 0, 1)
            b.slab(4, 4, -1, 2, 2)
            b.cube(Vec3i(3, 0, 1))
            b.switch(Vec3i(4, 0, 1), "g1")
            b.gravPad(Vec3i(7, 0, 0), 2)
            for (y in 0..2) for (z in 0..2) b.block(8, y, z)
            b.door(Vec3i(7, 2, 1), "g1")
            b.gravPad(Vec3i(7, 2, 2), 0)
            b.slab(5, 7, -1, 2, 2)
            b.goalOn(5, 2)
            b.spawnOn(0, 0)
            b.par = 18
            out.add(b.build())
        }
        run {
            val b = lb("w5l4", "TWO WALLS", 4, 4, "Change sides. Change fate. Change back.")
            b.slab(0, 6, -1, 0, 0)
            b.gravPad(Vec3i(6, 0, 0), 2)
            for (y in 0..2) for (z in -1..1) b.block(7, y, z)
            b.gravPad(Vec3i(6, 2, -1), 0)
            b.slab(2, 6, -1, -1, -1)
            b.block(4, 0, -1)
            b.goalOn(2, -1)
            b.spawnOn(0, 0)
            b.par = 16
            out.add(b.build())
        }
        run {
            val b = lb("w5l5", "WEIGHTLESS MASS", 4, 4, "Push the cube across a world that fell away.")
            b.slab(0, 6, -1, 0, 0)
            b.gravPad(Vec3i(6, 0, 0), 2)
            for (y in 0..2) for (z in 0..2) b.block(7, y, z)
            b.gravPad(Vec3i(6, 2, 2), 0)
            b.slab(2, 6, -1, 2, 2)
            b.removeAt(4, -1, 2)
            b.bridgeAt(4, -1, 2, 1)
            b.goalOn(2, 2)
            b.spawnOn(0, 0)
            b.par = 22
            out.add(b.build())
        }
        run {
            val b = lb("w5l6", "WELL OF WORLDS", 4, 4, "Fall forever. Land somewhere new.")
            b.slab(0, 6, -1, 0, 0)
            b.gravPad(Vec3i(6, 0, 0), 2)
            for (y in 0..2) for (z in 0..2) b.block(7, y, z)
            b.portal(Vec3i(6, 2, 2), Vec3i(9, 0, 0))
            b.gravPad(Vec3i(9, 0, 0), 0)
            b.slab(9, 13, -1, 0, 0)
            b.goalOn(13, 0)
            b.spawnOn(0, 0)
            b.par = 14
            out.add(b.build())
        }

        run {
            val b = lb("w6l1", "CONFLUENCE", 5, 5, "Everything meets here.")
            b.slab(0, 6, -1, 0, 0)
            b.slab(9, 15, -1, 6, 6)
            b.removeAt(3, -1, 0)
            b.bridgeAt(3, -1, 0, 1)
            b.portal(Vec3i(6, 0, 0), Vec3i(9, 0, 6))
            b.spawnOn(0, 0)
            b.goalOn(15, 6)
            b.par = 13
            out.add(b.build())
        }
        run {
            val b = lb("w6l2", "CLOCKWORK", 5, 5, "Timing is part of the geometry.")
            b.slab(0, 10, -1, 0, 0)
            b.timedDoor(Vec3i(5, 0, 0), 3.4f, 0.5f, 0.15f)
            b.door(Vec3i(8, 0, 0), "g1")
            b.switch(Vec3i(8, 0, 1), "g1")
            b.slab(7, 9, -1, 1, 1)
            b.spawnOn(0, 0)
            b.goalOn(10, 0)
            b.par = 13
            out.add(b.build())
        }
        run {
            val b = lb("w6l3", "PARADOX ENGINE", 5, 5, "Cubes weigh the same in every universe.")
            b.slab(0, 10, -1, 0, 0)
            b.slab(0, 3, -1, 2, 4)
            b.slab(0, 0, -1, 1, 1)
            b.removeAt(6, -1, 0)
            b.bridgeAt(6, -1, 0, 1)
            b.cube(Vec3i(1, 0, 3))
            b.switch(Vec3i(3, 0, 3), "g1")
            b.door(Vec3i(8, 0, 0), "g1")
            b.spawnOn(0, 0)
            b.goalOn(10, 0)
            b.par = 18
            out.add(b.build())
        }
        run {
            val b = lb("w6l4", "EVENT HORIZON", 5, 5, "Cross the gates before the century closes.")
            b.slab(0, 12, -1, 0, 0)
            b.timedDoor(Vec3i(4, 0, 0), 5.0f, 0.55f, 0.05f)
            b.timedDoor(Vec3i(9, 0, 0), 5.0f, 0.55f, 0.6f)
            b.spawnOn(0, 0)
            b.goalOn(12, 0)
            b.par = 13
            out.add(b.build())
        }
        run {
            val b = lb("w6l5", "ESCHER FALLS", 5, 5, "Two paradoxes walk hand in hand.")
            b.slab(0, 5, -1, 0, 0)
            b.gravPad(Vec3i(5, 0, 0), 2)
            for (y in 0..2) for (z in 0..2) b.block(6, y, z)
            b.gravPad(Vec3i(5, 2, 2), 0)
            b.slab(1, 5, -1, 2, 2)
            b.removeAt(3, -1, 2)
            b.bridgeAt(3, -1, 2, 1)
            b.goalOn(1, 2)
            b.spawnOn(0, 0)
            b.par = 16
            out.add(b.build())
        }
        run {
            val b = lb("w6l6", "THE LAST DOOR", 5, 5, "All of it. At once. Then silence.")
            b.slab(0, 11, -1, 0, 0)
            b.slab(0, 11, -1, 6, 6)
            b.removeAt(4, -1, 0)
            b.removeAt(8, -1, 0)
            b.bridgeAt(4, -1, 0, 1)
            b.bridgeAt(8, -1, 0, 0)
            b.slab(6, 6, -1, 1, 5)
            b.timedDoor(Vec3i(6, 0, 3), 6.0f, 0.5f, 0.3f)
            b.slab(8, 10, -1, 5, 5)
            b.door(Vec3i(9, 0, 6), "g1")
            b.switch(Vec3i(9, 0, 5), "g1")
            b.spawnOn(0, 0)
            b.goalOn(11, 6)
            b.par = 22
            out.add(b.build())
        }
    }
}
