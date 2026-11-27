package com.paradox.game.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

object GLUtil {
    fun compileShader(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(sh)
            GLES20.glDeleteShader(sh)
            throw RuntimeException("Shader compile failed: $log\n$src")
        }
        return sh
    }

    fun link(vs: Int, fs: Int, attrs: Map<String, Int>): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        for ((name, loc) in attrs) GLES20.glBindAttribLocation(p, loc, name)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Program link failed: $log")
        }
        return p
    }
}

class Mesh(vertices: FloatArray) {
    val count = vertices.size / 9
    val buffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(vertices); position(0) }
}

class MeshBuilder {
    private val verts = ArrayList<Float>(2048)

    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, r: Float, g: Float, b: Float) {
        verts += x; verts += y; verts += z
        verts += nx; verts += ny; verts += nz
        verts += r; verts += g; verts += b
    }

    private fun quad(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, n: Vec3, c: Vec3) {
        for (p in arrayOf(p0, p1, p2, p0, p2, p3)) v(p.x, p.y, p.z, n.x, n.y, n.z, c.x, c.y, c.z)
    }

    private fun tri(p0: Vec3, p1: Vec3, p2: Vec3, n: Vec3, c: Vec3) {
        for (p in arrayOf(p0, p1, p2)) v(p.x, p.y, p.z, n.x, n.y, n.z, c.x, c.y, c.z)
    }

    fun octahedron(cx: Float, cy: Float, cz: Float, r: Float, top: Vec3, side: Vec3) {
        val apexT = Vec3(cx, cy + r, cz)
        val apexB = Vec3(cx, cy - r, cz)
        val pts = arrayOf(
            Vec3(cx + r, cy, cz), Vec3(cx, cy, cz + r),
            Vec3(cx - r, cy, cz), Vec3(cx, cy, cz - r)
        )
        for (i in 0..3) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val nT = Vec3((a.x + b.x) / 2, cy + r * 0.4f, (a.z + b.z) / 2) - Vec3(cx, cy, cz)
            tri(apexT, b, a, nT.normalized(), top)
            val nB = Vec3((a.x + b.x) / 2, cy - r * 0.4f, (a.z + b.z) / 2) - Vec3(cx, cy, cz)
            tri(apexB, a, b, nB.normalized(), side)
        }
    }

    fun ring(cx: Float, cy: Float, cz: Float, r: Float, tube: Float, col: Vec3, segments: Int = 10) {
        for (i in 0 until segments) {
            val a = (i.toFloat() / segments) * 2f * Math.PI.toFloat()
            val px = cx + r * Math.cos(a.toDouble()).toFloat()
            val pz = cz + r * Math.sin(a.toDouble()).toFloat()
            cube(px, cy, pz, tube, tube * 0.7f, tube, col, col)
        }
    }

    fun chevronX(cx: Float, cy: Float, cz: Float, hy: Float, col: Vec3) {
        cube(cx + 0.14f, cy, cz, 0.10f, hy, 0.20f, col, col)
        cube(cx - 0.04f, cy, cz - 0.15f, 0.10f, hy, 0.09f, col, col)
        cube(cx - 0.04f, cy, cz + 0.15f, 0.10f, hy, 0.09f, col, col)
    }

    private fun quadGrad(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, n: Vec3, cLow: Vec3, cHigh: Vec3) {
        v(p0.x, p0.y, p0.z, n.x, n.y, n.z, cLow.x, cLow.y, cLow.z)
        v(p1.x, p1.y, p1.z, n.x, n.y, n.z, cLow.x, cLow.y, cLow.z)
        v(p2.x, p2.y, p2.z, n.x, n.y, n.z, cHigh.x, cHigh.y, cHigh.z)
        v(p0.x, p0.y, p0.z, n.x, n.y, n.z, cLow.x, cLow.y, cLow.z)
        v(p2.x, p2.y, p2.z, n.x, n.y, n.z, cHigh.x, cHigh.y, cHigh.z)
        v(p3.x, p3.y, p3.z, n.x, n.y, n.z, cHigh.x, cHigh.y, cHigh.z)
    }

    fun cube(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, top: Vec3, side: Vec3) {
        val x0 = cx - hx; val x1 = cx + hx
        val y0 = cy - hy; val y1 = cy + hy
        val z0 = cz - hz; val z1 = cz + hz
        val bottomCol = side * 0.22f
        quad(Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x0, y1, z1), Vec3(0f, 1f, 0f), top)
        quadGrad(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1), Vec3(0f, 0f, 1f), bottomCol, side)
        quadGrad(Vec3(x1, y0, z0), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(0f, 0f, -1f), bottomCol, side)
        quadGrad(Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(1f, 0f, 0f), bottomCol, side)
        quadGrad(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0), Vec3(-1f, 0f, 0f), bottomCol, side)
    }

    fun build(): Mesh {
        val arr = FloatArray(verts.size)
        for (i in verts.indexArray()) arr[i] = verts[i]
        return Mesh(arr)
    }
}

private fun ArrayList<Float>.indexArray(): IntRange = 0 until size

class Camera {
    var eye = Vec3(0f, 14f, 18f)
    var target = Vec3(0f, 0f, 0f)
    var fovDeg = 52f

    fun viewProjection(aspect: Float): Mat4 {
        val proj = Mat4.perspective(fovDeg * Math.PI.toFloat() / 180f, aspect, 0.1f, 240f)
        return Mat4.mul(proj, Mat4.lookAt(eye, target, Vec3.Y))
    }
}

data class Particle(
    var pos: Vec3, var vel: Vec3, var color: Vec3,
    var life: Float, var maxLife: Float, var size: Float, var gravity: Float = 0f
)

class GlGameRenderer : GLSurfaceView.Renderer {
    var camera = Camera()
    var bloomEnabled = true
    var onFrame: ((GlGameRenderer, Float) -> Unit)? = null

    private var solidProg = 0
    private var particleProg = 0
    private var blurYProg = 0
    private var compositeProg = 0

    private var uSolidVP = 0; private var uSolidModel = 0; private var uSolidTint = 0
    private var uSolidEmissive = 0; private var uSolidEye = 0
    private var uSolidFogColor = 0; private var uSolidFogNear = 0; private var uSolidFogFar = 0
    private var uParticleVP = 0
    private var uBlurDir = 0
    private var uCompScene = 0; private var uCompBloom = 0; private var uCompIntensity = 0

    private var sceneRT: RenderTarget? = null
    private var pingRT: RenderTarget? = null
    private var pongRT: RenderTarget? = null
    private var fullscreenBuffer: FloatBuffer? = null

    private var unitCube: Mesh? = null

    private val particleBuffer: FloatBuffer = ByteBuffer.allocateDirect(2048 * 7 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var particleCount = 0
    val particles = ArrayList<Particle>(256)

    var clearColor = Vec3(0.012f, 0.016f, 0.04f)
    var fogNear = 30f
    var fogFar = 110f
    var bloomIntensity = 1.0f

    var width = 1
    var height = 1
    private var lastFrameNanos = 0L
    var measuredFps = 60f

    fun spawnParticle(p: Particle) {
        camera.hashCode()
        if (particles.size < 900) particles.add(p)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        solidProg = GLUtil.link(
            GLUtil.compileShader(GLES20.GL_VERTEX_SHADER, SOLID_VS),
            GLUtil.compileShader(GLES20.GL_FRAGMENT_SHADER, SOLID_FS),
            mapOf("aPos" to 0, "aNormal" to 1, "aColor" to 2)
        )
        particleProg = GLUtil.link(
            GLUtil.compileShader(GLES20.GL_VERTEX_SHADER, PARTICLE_VS),
            GLUtil.compileShader(GLES20.GL_FRAGMENT_SHADER, PARTICLE_FS),
            mapOf("aPos" to 0, "aColor" to 1, "aSize" to 2)
        )
        val quadVs = GLUtil.compileShader(GLES20.GL_VERTEX_SHADER, QUAD_VS)
        val quadVs2 = GLUtil.compileShader(GLES20.GL_VERTEX_SHADER, QUAD_VS)
        blurYProg = GLUtil.link(quadVs2, GLUtil.compileShader(GLES20.GL_FRAGMENT_SHADER, BLOOM_FS), mapOf("aPos" to 0))
        compositeProg = GLUtil.link(quadVs2, GLUtil.compileShader(GLES20.GL_FRAGMENT_SHADER, COMPOSITE_FS), mapOf("aPos" to 0))

        uSolidVP = GLES20.glGetUniformLocation(solidProg, "uVP")
        uSolidModel = GLES20.glGetUniformLocation(solidProg, "uModel")
        uSolidTint = GLES20.glGetUniformLocation(solidProg, "uTint")
        uSolidEmissive = GLES20.glGetUniformLocation(solidProg, "uEmissive")
        uSolidEye = GLES20.glGetUniformLocation(solidProg, "uEye")
        uSolidFogColor = GLES20.glGetUniformLocation(solidProg, "uFogColor")
        uSolidFogNear = GLES20.glGetUniformLocation(solidProg, "uFogNear")
        uSolidFogFar = GLES20.glGetUniformLocation(solidProg, "uFogFar")
        uParticleVP = GLES20.glGetUniformLocation(particleProg, "uVP")
        uCompScene = GLES20.glGetUniformLocation(compositeProg, "uScene")
        uCompBloom = GLES20.glGetUniformLocation(compositeProg, "uBloom")
        uCompIntensity = GLES20.glGetUniformLocation(compositeProg, "uIntensity")

        val quad = floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f)
        fullscreenBuffer = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quad); position(0) }
        val cb = MeshBuilder()
        cb.cube(0f, 0f, 0f, 0.5f, 0.5f, 0.5f, Vec3(1f, 1f, 1f), Vec3(1f, 1f, 1f))
        unitCube = cb.build()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = maxOf(1, w); height = maxOf(1, h)
        GLES20.glViewport(0, 0, width, height)
        sceneRT?.release(); pingRT?.release(); pongRT?.release()
        sceneRT = if (bloomEnabled) RenderTarget(width, height, true) else null
        val bw = maxOf(8, width / 4)
        val bh = maxOf(8, height / 4)
        pingRT = if (bloomEnabled) RenderTarget(bw, bh, false) else null
        pongRT = if (bloomEnabled) RenderTarget(bw, bh, false) else null
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            val dt = (now - lastFrameNanos) / 1_000_000_000f
            measuredFps = measuredFps * 0.92f + (1f / maxOf(dt, 0.001f)) * 0.08f
        }
        lastFrameNanos = now
        val dt = 1f / maxOf(measuredFps, 20f, 1f)
        for (p in particles) {
            p.life -= dt
            p.pos = p.pos + p.vel * dt
            p.vel = Vec3(p.vel.x, p.vel.y - p.gravity * dt, p.vel.z)
        }
        var i = particles.size - 1
        while (i >= 0) { if (particles[i].life <= 0f) particles.removeAt(i); i-- }

        val scene = sceneRT
        val ping = pingRT
        val pong = pongRT
        val useBloom = bloomEnabled && scene != null && ping != null && pong != null

        if (useBloom) scene!!.bind() else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(clearColor.x, clearColor.y, clearColor.z, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        onFrame?.invoke(this, dt)

        updateParticleBuffer()
        drawParticles()

        if (useBloom && scene != null && ping != null && pong != null) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            ping.bind()
            drawPass(blurYProg, scene.tex[0])
            pong.bind()
            drawPass(blurYProg, ping.tex[0], dirX = 1f / ping.width)
            ping.bind()
            drawPass(blurYProg, pong.tex[0], dirY = 1f / pong.height)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(compositeProg)
            bindQuad()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scene.tex[0])
            GLES20.glUniform1i(uCompScene, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ping.tex[0])
            GLES20.glUniform1i(uCompBloom, 1)
            GLES20.glUniform1f(uCompIntensity, bloomIntensity)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }
    }

    private fun drawPass(prog: Int, tex: Int, dirX: Float = 0f, dirY: Float = 0f) {
        GLES20.glUseProgram(prog)
        bindQuad()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        if (uBlurDir >= 0) {
            GLES20.glUniform2f(uBlurDir, dirX, dirY)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }

    private fun bindQuad() {
        fullscreenBuffer?.position(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, fullscreenBuffer)
        GLES20.glEnableVertexAttribArray(0)
    }

    fun beginScene() {
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(solidProg)
        val vp = camera.viewProjection(width.toFloat() / maxOf(height, 1))
        GLES20.glUniformMatrix4fv(uSolidVP, 1, false, vp.m, 0)
        GLES20.glUniform3f(uSolidEye, camera.eye.x, camera.eye.y, camera.eye.z)
        GLES20.glUniform3f(uSolidFogColor, clearColor.x, clearColor.y, clearColor.z)
        GLES20.glUniform1f(uSolidFogNear, fogNear)
        GLES20.glUniform1f(uSolidFogFar, fogFar)
    }

    fun drawMesh(mesh: Mesh, model: Mat4, tint: Vec3, emissive: Float) {
        GLES20.glUseProgram(solidProg)
        mesh.buffer.position(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 36, mesh.buffer)
        mesh.buffer.position(3)
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 36, mesh.buffer)
        mesh.buffer.position(6)
        GLES20.glVertexAttribPointer(2, 3, GLES20.GL_FLOAT, false, 36, mesh.buffer)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glUniformMatrix4fv(uSolidModel, 1, false, model.m, 0)
        GLES20.glUniform3f(uSolidTint, tint.x, tint.y, tint.z)
        GLES20.glUniform1f(uSolidEmissive, emissive)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count)
    }

    fun drawCube(center: Vec3, size: Vec3, rotationY: Float, tint: Vec3, emissive: Float, mesh: Mesh? = null) {
        val model = Mat4.mul(Mat4.translation(center), Mat4.mul(Mat4.rotationY(rotationY), Mat4.scale(size)))
        drawMesh(mesh ?: unitCube!!, model, tint, emissive)
    }

    private fun updateParticleBuffer() {
        particleCount = minOf(particles.size, 2048)
        particleBuffer.position(0)
        for (i in 0 until particleCount) {
            val p = particles[i]
            particleBuffer.put(p.pos.x); particleBuffer.put(p.pos.y); particleBuffer.put(p.pos.z)
            particleBuffer.put(p.color.x); particleBuffer.put(p.color.y); particleBuffer.put(p.color.z)
            particleBuffer.put(p.size * (p.life / p.maxLife).coerceIn(0.15f, 1f))
        }
        particleBuffer.position(0)
    }

    private fun drawParticles() {
        if (particleCount == 0) return
        GLES20.glUseProgram(particleProg)
        val vp = camera.viewProjection(width.toFloat() / maxOf(height, 1))
        GLES20.glUniformMatrix4fv(uParticleVP, 1, false, vp.m, 0)
        particleBuffer.position(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 28, particleBuffer)
        particleBuffer.position(3)
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 28, particleBuffer)
        particleBuffer.position(6)
        GLES20.glVertexAttribPointer(2, 1, GLES20.GL_FLOAT, false, 28, particleBuffer)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, particleCount)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    companion object {
        private val SOLID_VS = """
            attribute vec3 aPos;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            uniform mat4 uVP;
            uniform mat4 uModel;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying vec3 vWorld;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vWorld = world.xyz;
                mat3 nm = mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz);
                vNormal = normalize(nm * aNormal);
                vColor = aColor;
                gl_Position = uVP * world;
            }
        """
        private val SOLID_FS = """
            precision mediump float;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying vec3 vWorld;
            uniform vec3 uTint;
            uniform float uEmissive;
            uniform vec3 uEye;
            uniform vec3 uFogColor;
            uniform float uFogNear;
            uniform float uFogFar;
            float hash(vec3 p) {
                p = fract(p * 0.3183099 + vec3(0.1, 0.17, 0.13));
                p *= 17.0;
                return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
            }
            void main() {
                vec3 n = normalize(vNormal);
                vec3 l1 = normalize(vec3(0.45, 1.0, 0.3));
                vec3 l2 = normalize(vec3(-0.7, 0.25, -0.6));
                vec3 l3 = normalize(vec3(0.2, -0.4, -0.9));
                float d1 = max(dot(n, l1), 0.0);
                float d2 = max(dot(n, l2), 0.0);
                float d3 = max(dot(n, l3), 0.0);
                vec3 viewDir = normalize(uEye - vWorld);
                float fres = pow(1.0 - clamp(dot(n, viewDir), 0.0, 1.0), 3.0);
                vec3 base = vColor * uTint;
                float ao = clamp(vWorld.y * 0.06 + 0.82, 0.75, 1.0);
                vec3 lit = base * (0.20 + d1 * 0.85 + d2 * 0.30 + d3 * 0.12) * ao;
                vec3 h1 = normalize(l1 + viewDir);
                float spec = pow(max(dot(n, h1), 0.0), 42.0) * 0.55;
                vec3 h2 = normalize(l2 + viewDir);
                spec += pow(max(dot(n, h2), 0.0), 24.0) * 0.22 * vec3(0.6, 0.35, 1.0);
                float rim = pow(1.0 - clamp(dot(n, viewDir), 0.0, 1.0), 3.0);
                vec3 rimC = rim * vec3(0.11, 0.34, 0.46);
                vec3 col = mix(lit, base * 1.15, uEmissive) + rimC * (1.0 - uEmissive * 0.45);
                col += mix(spec, base * spec * 2.2, 0.5) * (1.0 - uEmissive * 0.6);
                if (uEmissive < 0.02 && n.y > 0.9) {
                    vec2 f = abs(fract(vWorld.xz) - 0.5);
                    float line = smoothstep(0.42, 0.5, max(f.x, f.y));
                    col *= 1.0 - line * 0.22;
                    float grain = hash(floor(vWorld.xz * 2.0));
                    col *= 0.94 + grain * 0.09;
                }
                float dist = length(uEye - vWorld);
                float fog = clamp((dist - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
                col = mix(col, uFogColor, fog * 0.9);
                col = pow(max(col, 0.0), vec3(0.92));
                gl_FragColor = vec4(col, 1.0);
            }
        """
        private val PARTICLE_VS = """
            attribute vec3 aPos;
            attribute vec3 aColor;
            attribute float aSize;
            uniform mat4 uVP;
            varying vec3 vColor;
            void main() {
                vColor = aColor;
                gl_Position = uVP * vec4(aPos, 1.0);
                gl_PointSize = aSize;
            }
        """
        private val PARTICLE_FS = """
            precision mediump float;
            varying vec3 vColor;
            void main() {
                vec2 d = gl_PointCoord - vec2(0.5);
                float a = max(0.0, 1.0 - 2.0 * length(d));
                gl_FragColor = vec4(vColor, a * a);
            }
        """
        private val QUAD_VS = """
            attribute vec3 aPos;
            varying vec2 vUV;
            void main() { vUV = aPos.xy * 0.5 + 0.5; gl_Position = vec4(aPos.xy, 0.0, 1.0); }
        """
        private val BLOOM_FS = """
            precision mediump float;
            varying vec2 vUV;
            uniform sampler2D uTex;
            uniform vec2 uBlurDir;
            void main() {
                if (uBlurDir.x == 0.0 && uBlurDir.y == 0.0) {
                    vec3 c = texture2D(uTex, vUV).rgb;
                    float l = dot(c, vec3(0.299, 0.587, 0.114));
                    gl_FragColor = vec4(c * smoothstep(0.45, 0.9, l), 1.0);
                    return;
                }
                vec2 px = uBlurDir;
                vec3 c = texture2D(uTex, vUV).rgb * 0.227;
                c += (texture2D(uTex, vUV + px * 1.384).rgb + texture2D(uTex, vUV - px * 1.384).rgb) * 0.316;
                c += (texture2D(uTex, vUV + px * 3.230).rgb + texture2D(uTex, vUV - px * 3.230).rgb) * 0.070;
                gl_FragColor = vec4(c, 1.0);
            }
        """
        private val COMPOSITE_FS = """
            precision mediump float;
            varying vec2 vUV;
            uniform sampler2D uScene;
            uniform sampler2D uBloom;
            uniform float uIntensity;
            float hash2(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            void main() {
                vec3 c = texture2D(uScene, vUV).rgb;
                vec3 b = texture2D(uBloom, vUV).rgb;
                vec3 hdr = c + b * uIntensity;
                vec3 mapped = hdr / (1.0 + hdr * 0.25);
                vec2 d = vUV - 0.5;
                float vig = smoothstep(0.85, 0.25, length(d));
                mapped *= mix(0.62, 1.06, vig);
                float ca = 0.0022 * length(d);
                mapped.r *= 1.0 + ca;
                mapped.b *= 1.0 - ca * 0.7;
                float g = (hash2(vUV * vec2(913.0, 719.0)) - 0.5) * 0.035;
                mapped += g;
                mapped = pow(max(mapped, 0.0), vec3(0.95));
                gl_FragColor = vec4(mapped, 1.0);
            }
        """
    }
}

class RenderTarget(val width: Int, val height: Int, val withDepth: Boolean) {
    val fbo = IntArray(1)
    val tex = IntArray(1)
    private val depth = IntArray(1)

    init {
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
        if (withDepth) {
            GLES20.glGenRenderbuffers(1, depth, 0)
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depth[0])
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height)
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depth[0])
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glViewport(0, 0, width, height)
    }

    fun release() {
        GLES20.glDeleteFramebuffers(1, fbo, 0)
        GLES20.glDeleteTextures(1, tex, 0)
        if (withDepth) GLES20.glDeleteRenderbuffers(1, depth, 0)
    }
}
