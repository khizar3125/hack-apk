package com.paradox.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class AudioEngine {

    private val rate = 44100
    private var musicVol = 0.7f
    private var sfxVol = 0.8f
    private var muted = false

    private val sfxTracks = HashMap<Int, AudioTrack>()
    private var musicTrack: AudioTrack? = null
    private val rnd = Random(7)

    companion object {
        const val MOVE = 0
        const val PUSH = 1
        const val PORTAL = 2
        const val GRAVITY = 3
        const val PHASE = 4
        const val DEATH = 5
        const val WIN = 6
        const val CLICK = 7
        const val DENY = 8
        const val STAR = 9
    }

    fun setVolumes(music: Float, sfx: Float) {
        musicVol = music.coerceIn(0f, 1f)
        sfxVol = sfx.coerceIn(0f, 1f)
        musicTrack?.setVolume(musicVol * 0.5f)
    }

    fun setMuted(m: Boolean) {
        muted = m
    }

    fun isMuted() = muted

    fun resume() {
        val t = musicTrack
        if (t != null && t.playState != AudioTrack.PLAYSTATE_PLAYING) {
            t.reloadStaticData()
            t.play()
        }
    }

    fun pause() {
        try { musicTrack?.pause() } catch (_: Exception) {}
    }

    fun release() {
        sfxTracks.values.forEach { try { it.release() } catch (_: Exception) {} }
        sfxTracks.clear()
        try { musicTrack?.release() } catch (_: Exception) {}
        musicTrack = null
    }

    fun init() {
        buildMusic()
        prepare(MOVE, tone(880.0, 0.09f, decay = 9f, wave = ::tri))
        prepare(PUSH, thud())
        prepare(PORTAL, sweep(300.0, 1400.0, 0.4f))
        prepare(GRAVITY, sweep(700.0, 120.0, 0.45f))
        prepare(PHASE, tone(1320.0, 0.22f, decay = 5f, wave = ::sine2))
        prepare(DEATH, sweep(500.0, 60.0, 0.5f))
        prepare(WIN, arp(listOf(523.25, 659.25, 783.99, 1046.5, 1318.5)))
        prepare(CLICK, tone(1200.0, 0.045f, decay = 22f, wave = ::tri))
        prepare(DENY, tone(160.0, 0.12f, decay = 12f, wave = ::saw))
        prepare(STAR, tone(1568.0, 0.3f, decay = 4f, wave = ::sine2))
    }

    fun play(id: Int) {
        if (muted || sfxVol <= 0.01f) return
        val t = sfxTracks[id] ?: return
        try {
            t.pause()
            t.reloadStaticData()
            t.setVolume(sfxVol)
            t.play()
        } catch (_: Exception) {}
    }

    private fun prepare(id: Int, pcm: FloatArray) {
        val shortBuf = ShortArray(pcm.size)
        for (i in pcm.indices) shortBuf[i] = (pcm[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        val len = shortBuf.size
        val at = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(len * 2)
            .build()
        at.write(shortBuf, 0, len)
        at.setVolume(sfxVol)
        sfxTracks[id] = at
    }

    private fun buildMusic() {
        val n = rate * 12
        val buf = FloatArray(n)
        val chord = floatArrayOf(110f, 164.81f, 220f, 329.63f)
        for (i in 0 until n) {
            val t = i.toFloat() / rate
            var s = 0f
            for (k in chord.indices) {
                val f = chord[k]
                val wob = 1f + 0.002f * sin(t * (0.13f + k * 0.07f) * 2f * PI.toFloat())
                s += (0.055f / (k + 1)) * sin(2f * PI.toFloat() * f * wob * t + sin(t * 0.9f + k))
                s += (0.018f / (k + 1)) * sin(2f * PI.toFloat() * f * 2.01f * t)
            }
            s += 0.01f * (rnd.nextFloat() * 2f - 1f)
            val env = 0.6f + 0.4f * sin(2f * PI.toFloat() * t / 12f)
            buf[i] = s * env
        }
        val shortBuf = ShortArray(n)
        for (i in 0 until n) shortBuf[i] = (buf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(n * 2)
        val at = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(minBuf)
            .build()
        at.write(shortBuf, 0, n)
        at.setLoopPoints(0, n, -1)
        at.setVolume(musicVol * 0.5f)
        musicTrack = at
    }

    private fun sine(t: Float) = sin(2f * PI.toFloat() * t)
    private fun sine2(t: Float) = sin(2f * PI.toFloat() * t) + 0.35f * sin(4f * PI.toFloat() * t)
    private fun tri(t: Float) = 2f * abs(2f * (t - kotlin.math.floor(t + 0.5f)))
    private fun saw(t: Float) = 2f * (t - kotlin.math.floor(t + 0.5f))

    private fun tone(freq: Double, dur: Float, decay: Float, wave: (Float) -> Float): FloatArray {
        val n = (rate * dur).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / rate
            val env = exp(-decay * t)
            out[i] = env * 0.6f * wave((freq * t).toFloat())
        }
        return out
    }

    private fun sweep(f0: Double, f1: Double, dur: Float): FloatArray {
        val n = (rate * dur).toInt()
        val out = FloatArray(n)
        var phase = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = (f0 + (f1 - f0) * t * t).toFloat()
            phase += 2f * PI.toFloat() * f / rate
            val env = exp(-3f * t) * (1f - t * 0.2f)
            out[i] = (env * 0.5f * sin(phase)).toFloat()
        }
        return out
    }

    private fun thud(): FloatArray {
        val n = rate / 5
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / rate
            val env = exp(-18f * t)
            out[i] = (env * 0.8f * sin(2f * PI.toFloat() * 70f * t) + 0.3f * env * (rnd.nextFloat() * 2f - 1f) * exp(-40f * t)).toFloat()
        }
        return out
    }

    private fun arp(freqs: List<Double>): FloatArray {
        val noteLen = 0.14f
        val n = (rate * noteLen * freqs.size).toInt()
        val out = FloatArray(n)
        for (k in freqs.indices) {
            val start = (rate * noteLen * k).toInt()
            val len = (rate * noteLen * 1.4f).toInt().coerceAtMost(n - start)
            var phase = 0f
            for (i in 0 until len) {
                val t = i.toFloat() / rate
                phase += 2f * PI.toFloat() * freqs[k].toFloat() / rate
                val env = exp(-5f * t)
                out[start + i] += 0.4f * env * sin(phase)
            }
        }
        return out
    }
}
