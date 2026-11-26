package com.paradox.game

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.paradox.game.app.GameApp
import com.paradox.game.audio.AudioEngine
import com.paradox.game.data.SaveManager
import com.paradox.game.engine.GlGameRenderer
import com.paradox.game.render.SceneRenderer
import com.paradox.game.ui.UiLayer

class MainActivity : Activity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var overlay: Overlay
    private lateinit var app: GameApp
    private lateinit var audio: AudioEngine
    private lateinit var save: SaveManager
    private lateinit var scene: SceneRenderer

    class Overlay(ctx: Context, val uiHolder: () -> UiLayer?) : View(ctx) {
        private var lastT = 0L
        override fun onDraw(c: Canvas) {
            val ui = uiHolder() ?: return
            val now = System.nanoTime()
            val dt = if (lastT == 0L) 0.016f else ((now - lastT) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
            lastT = now
            ui.layout(width, height)
            ui.update(dt)
            ui.draw(c, now / 1_000_000_000f)
            postInvalidateOnAnimation()
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val ui = uiHolder() ?: return false
            return ui.onTouch(ev)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        save = SaveManager(filesDir)
        save.load()
        audio = AudioEngine()
        audio.setVolumes(save.data.settings.music, save.data.settings.sfx)
        audio.setMuted(save.data.settings.music <= 0.01f && save.data.settings.sfx <= 0.01f)
        audio.init()

        val renderer = GlGameRenderer()
        renderer.bloomEnabled = save.data.settings.bloom
        scene = SceneRenderer(renderer)
        app = GameApp(this, save, audio, scene)
        overlay = Overlay(this) { if (::app.isInitialized) app.ui else null }

        app.ui = UiLayer(app)

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        renderer.onFrame = { _, dt ->
            val state = app.logic?.state
            if (state != null && app.ui.screen == com.paradox.game.ui.Screen.PLAY) {
                app.anim.update(dt)
                scene.update(dt, state, app.anim)
            }
        }

        val frame = FrameLayout(this)
        frame.addView(glView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)
        immersive()
    }

    private fun immersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        audio.resume()
        immersive()
    }

    override fun onPause() {
        save.save()
        audio.pause()
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }
}
