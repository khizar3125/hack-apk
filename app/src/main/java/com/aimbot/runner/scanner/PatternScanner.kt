package com.aimbot.runner.scanner

import android.util.Log
import com.aimbot.runner.root.RootAccess

/**
 * Byte-pattern scanner for a game module.
 *
 * Scans module memory for a signature like "48 8B 05 ?? ?? ?? ?? 48 8B 40 10"
 * where "??" is a wildcard. Used to auto-detect entity lists, local player
 * pointers, camera structures, etc. without pre-dumped offsets.
 *
 * Reads go through RootAccess.readMemory() via `su`, so a rooted device is
 * required.
 */
class PatternScanner(private val pid: Int, private val moduleBase: Long, private val moduleSize: Long) {

    companion object {
        private const val TAG = "PatternScanner"
    }

    /** A pattern entry: hex bytes with "??" wildcards. */
    data class Pattern(val name: String, val signature: String)

    /** A match result. */
    data class Match(val name: String, val address: Long, val offsetFromBase: Long)

    /** Parse a signature string into bytes and a mask (0xff = check, 0x00 = wildcard). */
    private fun parsePattern(sig: String): Pair<ByteArray, BooleanArray> {
        val parts = sig.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val bytes = ByteArray(parts.size)
        val mask = BooleanArray(parts.size) { true }
        for (i in parts.indices) {
            val p = parts[i].uppercase()
            if (p == "??" || p == "?") {
                mask[i] = false
                bytes[i] = 0
            } else {
                bytes[i] = p.toInt(16).toByte()
            }
        }
        return bytes to mask
    }

    /**
     * Scan the module for [pattern] and return all matches.
     * Scans in 4 KiB blocks through /proc/<pid>/mem.
     */
    fun scan(pattern: Pattern): List<Match> {
        val (sigBytes, mask) = parsePattern(pattern.signature)
        val sigLen = sigBytes.size
        val matches = mutableListOf<Match>()

        val block = 4096
        var offset = 0L
        while (offset + sigLen <= moduleSize) {
            val addr = moduleBase + offset
            val data = RootAccess.readMemory(pid, addr, block.toInt()) ?: {
                offset += block
                return@while
            }()

            var i = 0
            while (i + sigLen <= data.size) {
                var matched = true
                for (j in 0 until sigLen) {
                    if (mask[j] && data[i + j] != sigBytes[j]) {
                        matched = false
                        break
                    }
                }
                if (matched) {
                    val absAddr = moduleBase + offset + i
                    matches.add(Match(pattern.name, absAddr, offset + i))
                    Log.i(TAG, "pattern ${pattern.name} at 0x${absAddr.toString(16)} (offset 0x${(offset + i).toString(16)})")
                }
                i++
            }

            // Overlap by sigLen-1 so patterns crossing block boundaries are found.
            offset += block - sigLen + 1
        }

        Log.i(TAG, "pattern ${pattern.name}: ${matches.size} matches")
        return matches
    }

    /** Scan for multiple patterns at once. */
    fun scanAll(vararg patterns: Pattern): List<Match> {
        val all = mutableListOf<Match>()
        for (p in patterns) {
            all.addAll(scan(p))
        }
        return all
    }

    /** Read a pointer (8 bytes, little-endian) at [address]. */
    fun readPtr(addr: Long): Long {
        val b = RootAccess.readMemory(pid, addr, 8) ?: return 0L
        return java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
    }

    /** Read a 4-byte int at [address]. */
    fun readInt(addr: Long): Int {
        val b = RootAccess.readMemory(pid, addr, 4) ?: return 0
        return java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
    }

    /** Read a 4-byte float at [address]. */
    fun readFloat(addr: Long): Float {
        val b = RootAccess.readMemory(pid, addr, 4) ?: return 0f
        return java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
    }
}
