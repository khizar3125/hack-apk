package com.paradox.game.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val l = length(); return if (l > 1e-7f) this * (1f / l) else Vec3(0f, 1f, 0f)
    }
    fun lerp(o: Vec3, t: Float) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    companion object {
        val Zero = Vec3(0f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
    }
}

data class Vec3i(val x: Int, val y: Int, val z: Int) {
    operator fun plus(o: Vec3i) = Vec3i(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3i) = Vec3i(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Int) = Vec3i(x * s, y * s, z * s)
    fun toV3() = Vec3(x.toFloat(), y.toFloat(), z.toFloat())
    fun manhattan(o: Vec3i) = abs(x - o.x) + abs(y - o.y) + abs(z - o.z)
    fun dot(o: Vec3i) = x * o.x + y * o.y + z * o.z
    companion object {
        val Zero = Vec3i(0, 0, 0)
        fun axis(i: Int) = when (i) {
            0 -> Vec3i(1, 0, 0); 1 -> Vec3i(-1, 0, 0)
            2 -> Vec3i(0, 1, 0); 3 -> Vec3i(0, -1, 0)
            4 -> Vec3i(0, 0, 1); else -> Vec3i(0, 0, -1)
        }
    }
}

class Mat4 private constructor(val m: FloatArray) {
    companion object {
        fun identity() = Mat4(floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        fun mul(a: Mat4, b: Mat4): Mat4 {
            val o = FloatArray(16)
            for (i in 0..3) for (j in 0..3) {
                var s = 0f
                for (k in 0..3) s += a.m[k * 4 + j] * b.m[i * 4 + k]
                o[i * 4 + j] = s
            }
            return Mat4(o)
        }
        fun perspective(fovY: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / kotlin.math.tan(fovY / 2f)
            val m = FloatArray(16)
            m[0] = f / aspect; m[5] = f
            m[10] = (far + near) / (near - far); m[11] = -1f
            m[14] = 2f * far * near / (near - far)
            return Mat4(m)
        }
        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalized()
            val s = f.cross(up).normalized()
            val u = s.cross(f)
            return Mat4(floatArrayOf(
                s.x, u.x, -f.x, 0f,
                s.y, u.y, -f.y, 0f,
                s.z, u.z, -f.z, 0f,
                -s.dot(eye), -u.dot(eye), f.dot(eye), 1f))
        }
        fun translation(t: Vec3) = Mat4(floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, t.x, t.y, t.z, 1f))
        fun rotationY(a: Float): Mat4 {
            val c = cos(a); val s = sin(a)
            return Mat4(floatArrayOf(c, 0f, -s, 0f, 0f, 1f, 0f, 0f, s, 0f, c, 0f, 0f, 0f, 0f, 1f))
        }
        fun rotationX(a: Float): Mat4 {
            val c = cos(a); val s = sin(a)
            return Mat4(floatArrayOf(1f, 0f, 0f, 0f, 0f, c, s, 0f, 0f, -s, c, 0f, 0f, 0f, 0f, 1f))
        }
        fun rotationZ(a: Float): Mat4 {
            val c = cos(a); val s = sin(a)
            return Mat4(floatArrayOf(c, s, 0f, 0f, -s, c, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        }
        fun scale(s: Vec3) = Mat4(floatArrayOf(
            s.x, 0f, 0f, 0f, 0f, s.y, 0f, 0f, 0f, 0f, s.z, 0f, 0f, 0f, 0f, 1f))
    }
}

object Acos {
    fun of(v: Float) = (2 * Math.atan2(Math.sqrt(1 - v.toDouble()), Math.sqrt(v.toDouble()))).toFloat()
}

fun shortestAngle(from: Float, to: Float): Float {
    var d = to - from
    while (d > Math.PI) d -= (2 * Math.PI).toFloat()
    while (d < -Math.PI) d += (2 * Math.PI).toFloat()
    return d
}

fun smoothStep(t: Float) = t * t * (3f - 2f * t)

fun easeOutBack(t: Float): Float {
    val c = 1.70158f
    val u = t - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}
