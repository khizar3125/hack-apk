package com.aimbot.runner.scanner

import android.util.Log
import com.aimbot.runner.root.RootAccess

/**
 * Real memory value scanner for a running process.
 *
 * Works like Cheat Engine / Game Guardian:
 *   1. First scan: find every address containing [value] of type [type].
 *   2. Filter scans: re-scan only previous results for [newValue].
 *   3. Changed/unchanged: keep addresses whose value changed or stayed same.
 *
 * All reads go through RootAccess.readMemory() which uses `su` to read
 * /proc/<pid>/mem. A rooted device is required.
 */
class MemoryScanner(private val pid: Int) {

    companion object {
        private const val TAG = "MemoryScanner"
    }

    /** A candidate address with its last-read value. */
    data class Candidate(
        val address: Long,
        var value: Any,
        var type: ScanType
    )

    enum class ScanType {
        INT, FLOAT, LONG, DOUBLE, BYTES
    }

    /** Region limits: scan only [start, end). */
    data class Region(val start: Long, val end: Long)

    private var candidates: MutableList<Candidate> = mutableListOf()
    private var region: Region? = null

    /** Set the address range to scan. Null = whole mapped range. */
    fun setRegion(start: Long, end: Long) {
        region = Region(start, end)
    }

    /** Clear all candidates. */
    fun clear() {
        candidates.clear()
    }

    /**
     * First scan: walk the region and collect every address whose value
     * matches [value] interpreted as [type].
     */
    fun firstScan(value: Any, type: ScanType, region: Region? = null): List<Candidate> {
        this.region = region ?: this.region
        if (this.region == null) {
            Log.w(TAG, "no scan region set")
            return emptyList()
        }

        candidates.clear()
        val r = this.region!!
        val step = 4 // scan aligned to 4 bytes
        var addr = r.start

        while (addr + step <= r.end) {
            val hit = readAndMatch(addr, value, type)
            if (hit != null) {
                candidates.add(Candidate(addr, hit, type))
            }
            addr += step
        }

        Log.i(TAG, "firstScan found ${candidates.size} candidates")
        return candidates.toList()
    }

    /**
     * Filter scan: re-read every existing candidate and keep only those
     * whose value now matches [value].
     */
    fun filterScan(value: Any, type: ScanType): List<Candidate> {
        val survivors = mutableListOf<Candidate>()
        for (c in candidates) {
            val cur = readValue(c.address, c.type)
            if (cur != null && valuesMatch(cur, value, type)) {
                c.value = cur
                survivors.add(c)
            }
        }
        candidates = survivors
        Log.i(TAG, "filterScan left ${candidates.size} candidates")
        return candidates.toList()
    }

    /**
     * Changed scan: keep only candidates whose value differs from the
     * value stored in the candidate.
     */
    fun changedScan(): List<Candidate> {
        val survivors = mutableListOf<Candidate>()
        for (c in candidates) {
            val cur = readValue(c.address, c.type)
            if (cur != null && !valuesMatch(cur, c.value, c.type)) {
                c.value = cur
                survivors.add(c)
            }
        }
        candidates = survivors
        Log.i(TAG, "changedScan left ${candidates.size} candidates")
        return candidates.toList()
    }

    /**
     * Unchanged scan: keep only candidates whose value is still the same.
     */
    fun unchangedScan(): List<Candidate> {
        val survivors = mutableListOf<Candidate>()
        for (c in candidates) {
            val cur = readValue(c.address, c.type)
            if (cur != null && valuesMatch(cur, c.value, c.type)) {
                survivors.add(c)
            }
        }
        candidates = survivors
        Log.i(TAG, "unchangedScan left ${candidates.size} candidates")
        return candidates.toList()
    }

    fun getCandidates(): List<Candidate> = candidates.toList()

    /** Read a value at [address] as [type]. */
    private fun readValue(addr: Long, type: ScanType): Any? {
        return when (type) {
            ScanType.INT -> {
                val b = RootAccess.readMemory(pid, addr, 4) ?: return null
                java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            }
            ScanType.FLOAT -> {
                val b = RootAccess.readMemory(pid, addr, 4) ?: return null
                java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
            }
            ScanType.LONG -> {
                val b = RootAccess.readMemory(pid, addr, 8) ?: return null
                java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
            }
            ScanType.DOUBLE -> {
                val b = RootAccess.readMemory(pid, addr, 8) ?: return null
                java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).double
            }
            ScanType.BYTES -> {
                RootAccess.readMemory(pid, addr, 4)?.toString()
            }
        }
    }

    /** Read at [addr] and check if it matches [expected] as [type]. */
    private fun readAndMatch(addr: Long, expected: Any, type: ScanType): Any? {
        val cur = readValue(addr, type)
        if (cur == null) return null
        if (valuesMatch(cur, expected, type)) return cur
        return null
    }

    private fun valuesMatch(actual: Any, expected: Any, type: ScanType): Boolean {
        return when (type) {
            ScanType.INT -> actual is Int && expected is Int && actual == expected
            ScanType.FLOAT -> actual is Float && expected is Float && actual == expected
            ScanType.LONG -> actual is Long && expected is Long && actual == expected
            ScanType.DOUBLE -> actual is Double && expected is Double && actual == expected
            ScanType.BYTES -> actual == expected
        }
    }

    /** Write a value to an address (used to set a found offset). */
    fun write(addr: Long, value: Any, type: ScanType): Boolean {
        val bytes = when (type) {
            ScanType.INT -> {
                val b = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putInt(value as Int).array()
                b
            }
            ScanType.FLOAT -> {
                val b = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putFloat(value as Float).array()
                b
            }
            ScanType.LONG -> {
                val b = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putLong(value as Long).array()
                b
            }
            ScanType.DOUBLE -> {
                val b = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putDouble(value as Double).array()
                b
            }
            ScanType.BYTES -> return false
        }
        val hex = bytes.joinToString("") { "\\x%02x".format(it) }
        val out = RootAccess.runShell(
            "printf '$hex' | dd of=/proc/$pid/mem bs=1 seek=$addr conv=notrunc 2>/dev/null"
        )
        return out.isNotEmpty()
    }

    fun candidateCount(): Int = candidates.size
}
