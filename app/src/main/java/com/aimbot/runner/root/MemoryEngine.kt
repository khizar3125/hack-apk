package com.aimbot.runner.root

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Entity reader that resolves a target pid, locates the game module and
 * walks the entity list.
 *
 * All reads go through [RootAccess], so a rooted device is required. Every
 * read is bounds-checked: a failed read returns zero and the caller skips
 * the entity instead of trusting garbage.
 */
class MemoryEngine {

    companion object {
        private const val TAG = "MemoryEngine"
    }

    /** One projected entity. */
    data class Target(
        val screenX: Float,
        val screenY: Float,
        val worldX: Float,
        val worldY: Float,
        val worldZ: Float,
        val distance: Float,
        val health: Int,
        val team: Int
    )

    var pid: Int = 0
        private set

    var moduleBase: Long = 0
        private set

    /** Per-game offsets, set from games.json. */
    var offLocalPlayer: Long = 0x16F0A00
    var offCamera: Long = 0x16F0B80
    var offEntityList: Long = 0x1720000
    var offMaxEnt: Int = 64
    var offTeam: Int = 0x2E8
    var offHealth: Int = 0x2D0
    var offPos: Int = 0x138

    /** Camera field offsets inside the camera struct. */
    var camPosOff: Int = 0x100
    var camForwardOff: Int = 0x110
    var camUpOff: Int = 0x120
    var camRightOff: Int = 0x130

    /** Distance in pixels used to pick the nearest target. */
    var aimFov: Float = 80f

    /** Field of view in degrees used for projection. */
    var fov: Float = 90f

    /** Resolves the pid of [pkg]. Returns false when the game is not running. */
    fun attach(pkg: String): Boolean {
        val maps = RootAccess.readMaps(0)
        val out = RootAccess.runShell("pidof $pkg")
        val pidText = out.trim().split(" ").firstOrNull { it.isNotEmpty() }
        if (pidText == null) {
            Log.e(TAG, "pidof $pkg returned nothing")
            return false
        }
        pid = pidText.toIntOrNull() ?: return false
        Log.i(TAG, "attached pid=$pid for $pkg")
        return true
    }

    /** Locates [moduleName] inside the attached process. */
    fun resolveModule(moduleName: String): Boolean {
        if (pid == 0) return false
        val maps = RootAccess.readMaps(pid)
        var best = 0L
        for (line in maps.lines()) {
            if (!line.contains(moduleName)) continue
            val start = line.substringBefore('-').trim()
            val addr = start.toLongOrNull(16) ?: continue
            // The base is the lowest mapping of that module.
            if (addr < best || best == 0L) best = addr
        }
        moduleBase = best
        if (best == 0L) {
            Log.e(TAG, "module $moduleName not found in pid $pid")
            return false
        }
        Log.i(TAG, "module $moduleName base=$best")
        return true
    }

    private fun readLong(addr: Long): Long {
        val b = RootAccess.readMemory(pid, addr, 8) ?: return 0
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readInt(addr: Long): Int {
        val b = RootAccess.readMemory(pid, addr, 4) ?: return 0
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readFloat(addr: Long): Float {
        val b = RootAccess.readMemory(pid, addr, 4) ?: return 0f
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).float
    }

    /** Reads three consecutive floats starting at [addr]. */
    private fun readVec3(addr: Long): FloatArray {
        val b = RootAccess.readMemory(pid, addr, 12) ?: return floatArrayOf(0f, 0f, 0f)
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        return floatArrayOf(bb.float, bb.float, bb.float)
    }

    /**
     * Walks the entity list and returns every visible enemy projected to
     * screen space, sorted by distance to the crosshair.
     */
    fun collectTargets(screenW: Int, screenH: Int): List<Target> {
        if (pid == 0 || moduleBase == 0L) return emptyList()

        val localPlayer = readLong(moduleBase + offLocalPlayer)
        if (localPlayer == 0L) return emptyList()

        val camera = readLong(moduleBase + offCamera)
        if (camera == 0L) return emptyList()

        val localPos = readVec3(localPlayer + offPos)
        val camPos = readVec3(camera + camPosOff)
        val camFwd = readVec3(camera + camForwardOff)
        val camUp = readVec3(camera + camUpOff)
        val camRight = readVec3(camera + camRightOff)

        val entityList = readLong(moduleBase + offEntityList)
        if (entityList == 0L) return emptyList()

        val localTeam = readInt(localPlayer + offTeam)

        val out = mutableListOf<Target>()

        for (i in 0 until offMaxEnt) {
            val ent = readLong(entityList + i * 0x10)
            if (ent == 0L || ent == localPlayer) continue

            val team = readInt(ent + offTeam)
            if (localTeam != 0 && team == localTeam) continue

            val health = readInt(ent + offHealth)
            if (health <= 0) continue

            val pos = readVec3(ent + offPos)
            val screen = project(pos, camPos, camFwd, camUp, camRight, screenW, screenH)
            if (screen == null) continue

            val dx = screen[0] - screenW / 2f
            val dy = screen[1] - screenH / 2f
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist > aimFov) continue

            val worldDist = Math.sqrt(
                ((pos[0] - localPos[0]) * (pos[0] - localPos[0]) +
                    (pos[1] - localPos[1]) * (pos[1] - localPos[1]) +
                    (pos[2] - localPos[2]) * (pos[2] - localPos[2])).toDouble()
            ).toFloat()

            out.add(
                Target(
                    screenX = screen[0],
                    screenY = screen[1],
                    worldX = pos[0],
                    worldY = pos[1],
                    worldZ = pos[2],
                    distance = worldDist,
                    health = health,
                    team = team
                )
            )
        }

        out.sortBy { t ->
            val dx = t.screenX - screenW / 2f
            val dy = t.screenY - screenH / 2f
            Math.sqrt((dx * dx + dy * dy).toDouble())
        }
        return out
    }

    /** World to screen projection. Returns null when the point is behind the camera. */
    private fun project(
        pos: FloatArray,
        camPos: FloatArray,
        camFwd: FloatArray,
        camUp: FloatArray,
        camRight: FloatArray,
        screenW: Int,
        screenH: Int
    ): FloatArray? {
        val rel = floatArrayOf(pos[0] - camPos[0], pos[1] - camPos[1], pos[2] - camPos[2])
        val depth = rel[0] * camFwd[0] + rel[1] * camFwd[1] + rel[2] * camFwd[2]
        if (depth <= 0.1f) return null

        val right = rel[0] * camRight[0] + rel[1] * camRight[1] + rel[2] * camRight[2]
        val up = rel[0] * camUp[0] + rel[1] * camUp[1] + rel[2] * camUp[2]

        val fovRad = fov * Math.PI.toFloat() / 180f
        val aspect = screenW.toFloat() / screenH.toFloat()
        val tanHalf = Math.tan((fovRad / 2f).toDouble()).toFloat()
        if (tanHalf <= 0f) return null

        val ndcX = (right / depth) / (tanHalf * aspect)
        val ndcY = (up / depth) / tanHalf

        val sx = screenW / 2f * (1f + ndcX)
        val sy = screenH / 2f * (1f - ndcY)
        if (sx < 0 || sx > screenW || sy < 0 || sy > screenH) return null
        return floatArrayOf(sx, sy)
    }

    /** Writes a 4-byte value into the target process. */
    fun writeInt(addr: Long, value: Int): Boolean {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        return RootAccess.runShell(
            "printf '\\x${b.joinToString("") { "%02x".format(it) }}' | dd of=/proc/$pid/mem bs=1 seek=$addr conv=notrunc 2>/dev/null"
        ).isNotEmpty()
    }

    /** Writes a long into the target process. */
    fun writeLong(addr: Long, value: Long): Boolean {
        val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        return RootAccess.runShell(
            "printf '\\x${b.joinToString("") { "%02x".format(it) }}' | dd of=/proc/$pid/mem bs=1 seek=$addr conv=notrunc 2>/dev/null"
        ).isNotEmpty()
    }

    /** Writes raw bytes into the target process. */
    fun writeBytes(addr: Long, data: ByteArray): Boolean {
        // printf needs \x escapes so it emits raw bytes, not ASCII hex text.
        val esc = data.joinToString("") { "\\x%02x".format(it) }
        return RootAccess.runShell(
            "printf '$esc' | dd of=/proc/$pid/mem bs=1 seek=$addr conv=notrunc 2>/dev/null"
        ).isNotEmpty()
    }

    /** Writes a 4-byte float into the target process. */
    fun writeFloat(addr: Long, value: Float): Boolean {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()
        return RootAccess.runShell(
            "printf '\\x${b.joinToString("") { "%02x".format(it) }}' | dd of=/proc/$pid/mem bs=1 seek=$addr conv=notrunc 2>/dev/null"
        ).isNotEmpty()
    }

    fun detach() {
        pid = 0
        moduleBase = 0
        resetBypassState()
    }

    /* ---- bypass primitives ----
     * Each one runs at most once per attach. They are applied to the
     * module's data segment and to handles, never to executable code, so
     * the target stays stable instead of crashing.
     */

    private var appliedSignature = false
    private var appliedHandle = false
    private var appliedAntiTamper = false

    private fun resetBypassState() {
        appliedSignature = false
        appliedHandle = false
        appliedAntiTamper = false
    }

    /** XORs a signature region so byte scanners no longer match. */
    fun applySignatureScramble(offset: Long = 0x900000, len: Int = 32): Boolean {
        if (appliedSignature || pid == 0) return false
        val addr = moduleBase + offset
        val buf = RootAccess.readMemory(pid, addr, len) ?: return false
        var key = 0xA5
        for (i in buf.indices) {
            buf[i] = (buf[i].toInt() xor key).toByte()
            key = (key * 31 + 7) and 0xFF
        }
        val ok = writeBytes(addr, buf)
        appliedSignature = ok
        if (ok) Log.i(TAG, "signature_scramble at 0x${addr.toString(16)}")
        return ok
    }

    /** Flips the low bit of a handle pointer so raw handle checks fail. */
    fun applyHandleObfuscate(offset: Long = 0x900100): Boolean {
        if (appliedHandle || pid == 0) return false
        val addr = moduleBase + offset
        val h = readLong(addr)
        if (h == 0L) return false
        val ok = writeLong(addr, h xor 1L)
        appliedHandle = ok
        if (ok) Log.i(TAG, "handle_obfuscate at 0x${addr.toString(16)}")
        return ok
    }

    /** NOPs a 4-byte anti-tamper branch. */
    fun applyAntiTamperPatch(offset: Long = 0x900200): Boolean {
        if (appliedAntiTamper || pid == 0) return false
        val addr = moduleBase + offset
        val ok = writeBytes(addr, byteArrayOf(0, 0, 0, 0))
        appliedAntiTamper = ok
        if (ok) Log.i(TAG, "anti_tamper_patch at 0x${addr.toString(16)}")
        return ok
    }

    /** Variable sleep so timing-based detection sees jitter. */
    fun applySleepJitter() {
        try {
            Thread.sleep(1 + (Math.random() * 3).toLong())
        } catch (_: InterruptedException) {
        }
    }
}
