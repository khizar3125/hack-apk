package com.aimbot.runner.script

object NativeBridge {
    external fun nativeInit(): Unit
    external fun nativeStart(gamePkg: String, moduleName: String, screenW: Int, screenH: Int): Boolean
    external fun nativeStop(): Unit
    external fun nativeIsRunning(): Boolean
    external fun nativeBypass(type: Int): Unit
    external fun nativeAimTick(screenW: Int, screenH: Int, fov: Float, smooth: Float, aimFov: Float): FloatArray
    external fun nativeStatus(): String

    init {
        System.loadLibrary("runner")
    }
}
