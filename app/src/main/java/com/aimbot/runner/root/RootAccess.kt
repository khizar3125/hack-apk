package com.aimbot.runner.root

import android.util.Log
import java.io.DataInputStream
import java.io.InputStream

/**
 * Root access helpers.
 *
 * Reading another app's memory from a normal Android app is blocked by
 * SELinux and ptrace_scope, so every /proc read goes through `su`.
 * Reads are aligned to 4 KiB blocks because dd seeks in blocks; the caller
 * gets the exact slice it asked for.
 */
object RootAccess {

    private const val TAG = "RootAccess"
    private const val BLOCK = 4096

    @Volatile
    var isRooted: Boolean = false
        private set

    @Volatile
    var rootDenied: Boolean = false
        private set

    /** Probes for a working su. Call once before the engine starts. */
    fun probe(): Boolean {
        if (isRooted) return true
        val out = runShell("id -u")
        val ok = out.trim() == "0"
        isRooted = ok
        rootDenied = !ok && out.contains("Permission denied")
        Log.i(TAG, "root probe uid=${out.trim()} ok=$ok denied=$rootDenied")
        return ok
    }

    /** Runs a shell command as root and returns stdout. */
    fun runShell(cmd: String): String {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            out
        } catch (e: Exception) {
            Log.e(TAG, "shell failed: ${e.message}")
            ""
        }
    }

    /** /proc/<pid>/maps contents, or empty on failure. */
    fun readMaps(pid: Int): String = runShell("cat /proc/$pid/maps")

    /**
     * Reads [len] bytes at [addr] from the target process.
     * Returns null when the range is not mapped.
     */
    fun readMemory(pid: Int, addr: Long, len: Int): ByteArray? {
        if (addr <= 0 || len <= 0 || len > 1 shl 20) return null
        val start = (addr / BLOCK) * BLOCK
        val blocks = ((addr - start + len) + BLOCK - 1) / BLOCK
        val cmd = "dd if=/proc/$pid/mem bs=$BLOCK skip=${start / BLOCK} count=$blocks 2>/dev/null"
        val raw = readBinary(cmd) ?: return null
        val offset = (addr - start).toInt()
        if (raw.size < offset + len) return null
        return raw.copyOfRange(offset, offset + len)
    }

    /** Reads a raw byte stream from a root shell command. */
    private fun readBinary(cmd: String): ByteArray? {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(false).start()
            val data = readAll(p.inputStream)
            p.waitFor()
            if (data.isEmpty()) null else data
        } catch (e: Exception) {
            Log.e(TAG, "binary read failed: ${e.message}")
            null
        }
    }

    private fun readAll(stream: InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16384)
        val input = DataInputStream(stream)
        while (true) {
            val n = try {
                input.read(chunk)
            } catch (e: Exception) {
                -1
            }
            if (n <= 0) break
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }
}
