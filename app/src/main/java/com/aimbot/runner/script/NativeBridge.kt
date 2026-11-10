package com.aimbot.runner.script

/**
 * The memory engine lives in Kotlin now: reading another app's memory needs
 * root, and driving a root shell is simpler than a native helper that would
 * run unprivileged and be killed by SELinux anyway.
 *
 * The native library is still shipped for the bypass primitives, but the
 * loader is lazy so a missing or broken lib never takes the app down at
 * startup.
 */
object NativeBridge {

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var loadFailed = false

    /** Loads the native library on first use. Never throws. */
    private fun ensureLibrary(): Boolean {
        if (libraryLoaded) return true
        if (loadFailed) return false
        return try {
            System.loadLibrary("runner")
            libraryLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            loadFailed = true
            android.util.Log.e("NativeBridge", "native library unavailable: ${e.message}")
            false
        }
    }

    fun nativeInit() {
        ensureLibrary()
    }

    fun nativeStop() {
        if (!libraryLoaded) return
        try {
            nativeStopImpl()
        } catch (e: UnsatisfiedLinkError) {
            loadFailed = true
        }
    }

    fun nativeIsRunning(): Boolean = false

    fun nativeStatus(): String = "kotlin engine"

    fun nativeBypass(type: Int) {
        if (!ensureLibrary()) return
        try {
            nativeBypassImpl(type)
        } catch (e: UnsatisfiedLinkError) {
            loadFailed = true
        }
    }

    private external fun nativeStopImpl()
    private external fun nativeBypassImpl(type: Int)
}
