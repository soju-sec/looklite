package app.looklite.android.engine

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import app.looklite.android.model.ColorBalance
import app.looklite.android.model.HslChannels
import app.looklite.android.model.SliderValues
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class LookLiteRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var programId = 0
    private var vaoId = 0
    private var vboId = 0

    private var imageTexId = 0
    private var lutTexId = 0

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    @Volatile
    var lastFrameTimeMs: Float = 0f
        private set
    @Volatile
    var glRendererName: String = "OpenGL ES 3.0"
        private set

    // State updated from main/UI thread
    private val lock = Any()
    private var pendingBitmap: Bitmap? = null
    private var pendingLutBuffer: ByteBuffer? = null
    private var lutSize = 17
    private var lutStage = 0 // 0 = early, 1 = late
    private var lutIntensity = 1.0f
    private var showBefore = false
    private var splitPos = -1.0f // < 0 means split off
    private var autoColorEnabled = false
    private var autoMin = floatArrayOf(0f, 0f, 0f)
    private var autoMax = floatArrayOf(1f, 1f, 1f)
    private var autoWb = floatArrayOf(1f, 1f, 1f)

    private var currentSliders = SliderValues()

    // Uniform locations cache
    private val locs = mutableMapOf<String, Int>()

    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).run {
        order(ByteOrder.nativeOrder())
        asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            ))
            position(0)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertCode = readAsset("shaders/looklite_native.vert")
        val fragCode = readAsset("shaders/looklite_native.frag")

        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertCode)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragCode)

        programId = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertShader)
            GLES30.glAttachShader(it, fragShader)
            GLES30.glLinkProgram(it)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(it, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(it)
                GLES30.glDeleteProgram(it)
                throw RuntimeException("OpenGL Program link failed: $log")
            }
        }

        cacheUniformLocations()

        // Setup Quad VAO/VBO
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, quadVertices, GLES30.GL_STATIC_DRAW)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)

        // Setup Textures
        val textures = IntArray(2)
        GLES30.glGenTextures(2, textures, 0)
        imageTexId = textures[0]
        lutTexId = textures[1]

        // Default 2D texture
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // Default 3D LUT texture
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        // Upload identity LUT initially
        val identity = LutLoader.createIdentityLut(17)
        upload3DLut(identity, 17)
        glRendererName = GLES30.glGetString(GLES30.GL_RENDERER) ?: "OpenGL ES 3.0"
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStartNs = System.nanoTime()
        var bmpToUpload: Bitmap? = null
        var lutToUpload: ByteBuffer? = null
        var currentLutSize = 17

        synchronized(lock) {
            if (pendingBitmap != null) {
                bmpToUpload = pendingBitmap
                pendingBitmap = null
            }
            if (pendingLutBuffer != null) {
                lutToUpload = pendingLutBuffer
                pendingLutBuffer = null
                currentLutSize = lutSize
            }
        }

        bmpToUpload?.let { bmp ->
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTexId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        }

        lutToUpload?.let { buf ->
            upload3DLut(buf, currentLutSize)
        }

        GLES30.glClearColor(0.08f, 0.08f, 0.09f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(programId)

        // Bind Textures
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTexId)
        setUniform1i("uImage", 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexId)
        setUniform1i("uLut", 1)

        // Push uniforms
        synchronized(lock) {
            setUniform1f("uLutSize", lutSize.toFloat())
            setUniform1f("uLutIntensity", lutIntensity)
            setUniform1i("uLutStage", lutStage)
            setUniform1i("uShowBefore", if (showBefore) 1 else 0)
            setUniform1f("uSplitPos", splitPos)
            setUniform1i("uAutoColor", if (autoColorEnabled) 1 else 0)
            setUniform3f("uAutoMin", autoMin[0], autoMin[1], autoMin[2])
            setUniform3f("uAutoMax", autoMax[0], autoMax[1], autoMax[2])
            setUniform3f("uAutoWb", autoWb[0], autoWb[1], autoWb[2])

            setUniform2f("uResolution", surfaceWidth.toFloat(), surfaceHeight.toFloat())

            val s = currentSliders
            setUniform1f("uExposure", s.exposure)
            setUniform1f("uContrast", s.contrast)
            setUniform1f("uHighlights", s.highlights)
            setUniform1f("uShadows", s.shadows)
            setUniform1f("uWhites", s.whites)
            setUniform1f("uBlacks", s.blacks)
            setUniform1f("uTemp", s.temperature)
            setUniform1f("uTint", s.tint)
            setUniform1f("uVibrance", s.vibrance)
            setUniform1f("uSaturation", s.saturation)
            setUniform1f("uVignette", s.vignette)
            setUniform1f("uGrainIntensity", s.grainIntensity)
            setUniform1f("uGrainSize", s.grainSize)
            setUniform1f("uSkinSmooth", s.skinSmooth)
            setUniform1f("uSkinRadiance", s.skinRadiance)
            setUniform1f("uClarity", s.clarity)
            setUniform1f("uGlow", s.glow)
            setUniform1f("uSharpness", s.sharpness)

            val hsl = s.hsl
            setUniform3f("uHslRed", hsl.red.h, hsl.red.s, hsl.red.l)
            setUniform3f("uHslOrange", hsl.orange.h, hsl.orange.s, hsl.orange.l)
            setUniform3f("uHslYellow", hsl.yellow.h, hsl.yellow.s, hsl.yellow.l)
            setUniform3f("uHslGreen", hsl.green.h, hsl.green.s, hsl.green.l)
            setUniform3f("uHslAqua", hsl.aqua.h, hsl.aqua.s, hsl.aqua.l)
            setUniform3f("uHslBlue", hsl.blue.h, hsl.blue.s, hsl.blue.l)
            setUniform3f("uHslPurple", hsl.purple.h, hsl.purple.s, hsl.purple.l)
            setUniform3f("uHslMagenta", hsl.magenta.h, hsl.magenta.s, hsl.magenta.l)

            val cb = s.colorBalance
            setUniform3f("uBalShadows", cb.shadows[0], cb.shadows[1], cb.shadows[2])
            setUniform3f("uBalMids", cb.midtones[0], cb.midtones[1], cb.midtones[2])
            setUniform3f("uBalHighlights", cb.highlights[0], cb.highlights[1], cb.highlights[2])
        }

        // Render Quad
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        lastFrameTimeMs = (System.nanoTime() - frameStartNs) / 1_000_000f
    }

    private fun upload3DLut(buffer: ByteBuffer, size: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexId)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8,
            size, size, size, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buffer
        )
    }

    fun setImageBitmap(bitmap: Bitmap) {
        val (minRgb, maxRgb, wbRgb) = analyzeAutoColor(bitmap)
        synchronized(lock) {
            pendingBitmap = bitmap
            autoMin = minRgb
            autoMax = maxRgb
            autoWb = wbRgb
        }
    }

    fun setAutoColor(enabled: Boolean) {
        synchronized(lock) {
            autoColorEnabled = enabled
        }
    }

    private fun analyzeAutoColor(bitmap: Bitmap): Triple<FloatArray, FloatArray, FloatArray> {
        val sampleW = 64
        val sampleH = 64
        val stepX = (bitmap.width / sampleW).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleH).coerceAtLeast(1)

        val histR = IntArray(256)
        val histG = IntArray(256)
        val histB = IntArray(256)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val px = bitmap.getPixel(x, y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                histR[r]++
                histG[g]++
                histB[b]++
                sumR += r
                sumG += g
                sumB += b
                count++
                x += stepX
            }
            y += stepY
        }

        if (count == 0) {
            return Triple(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f))
        }

        fun percentile(hist: IntArray, pct: Float): Float {
            val target = (count * pct).toInt()
            var acc = 0
            for (i in 0..255) {
                acc += hist[i]
                if (acc >= target) return i / 255f
            }
            return 1f
        }

        val minRgb = floatArrayOf(
            percentile(histR, 0.02f).coerceAtMost(0.35f),
            percentile(histG, 0.02f).coerceAtMost(0.35f),
            percentile(histB, 0.02f).coerceAtMost(0.35f)
        )
        val maxRgb = floatArrayOf(
            percentile(histR, 0.98f).coerceAtLeast(0.65f),
            percentile(histG, 0.98f).coerceAtLeast(0.65f),
            percentile(histB, 0.98f).coerceAtLeast(0.65f)
        )

        val meanR = (sumR.toFloat() / count) / 255f
        val meanG = (sumG.toFloat() / count) / 255f
        val meanB = (sumB.toFloat() / count) / 255f
        val avgGray = (meanR + meanG + meanB) / 3f

        fun safeGain(mean: Float): Float {
            val raw = if (mean > 0.05f) avgGray / mean else 1f
            // Blend 55% toward neutral gain and clamp so natural ambience stays intact
            return (1f + (raw - 1f) * 0.55f).coerceIn(0.84f, 1.20f)
        }

        val wbRgb = floatArrayOf(safeGain(meanR), safeGain(meanG), safeGain(meanB))
        return Triple(minRgb, maxRgb, wbRgb)
    }

    fun setLut(buffer: ByteBuffer, size: Int = 17, stage: String = "early") {
        synchronized(lock) {
            pendingLutBuffer = buffer
            lutSize = size
            lutStage = if (stage == "late") 1 else 0
        }
    }

    fun setIntensity(intensityPercent: Int) {
        synchronized(lock) {
            lutIntensity = (intensityPercent / 100f).coerceIn(0f, 1f)
        }
    }

    fun setSliders(sliders: SliderValues) {
        synchronized(lock) {
            currentSliders = sliders
        }
    }

    fun setShowBefore(show: Boolean) {
        synchronized(lock) {
            showBefore = show
        }
    }

    fun setSplitPosition(pos: Float) {
        synchronized(lock) {
            splitPos = pos
        }
    }

    /**
     * High-Resolution Offscreen Render for Export (MUST be called on GLThread via glView.queueEvent)
     */
    fun renderOffscreen(sourceBitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val uploadBitmap = if (sourceBitmap.config != Bitmap.Config.ARGB_8888) {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            sourceBitmap
        }

        val fbo = IntArray(1)
        val renderTex = IntArray(1)
        val exportSrcTex = IntArray(1)

        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glGenTextures(1, renderTex, 0)
        GLES30.glGenTextures(1, exportSrcTex, 0)

        // Target color attachment texture
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderTex[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            targetWidth, targetHeight, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // Temporary source texture so preview imageTexId remains untouched
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, exportSrcTex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, uploadBitmap, 0)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, renderTex[0], 0
        )

        val prevW = surfaceWidth
        val prevH = surfaceHeight
        val prevImageTex = imageTexId
        val origSplit = splitPos
        val origShowBefore = showBefore

        surfaceWidth = targetWidth
        surfaceHeight = targetHeight
        imageTexId = exportSrcTex[0]
        splitPos = -1f
        showBefore = false
        GLES30.glViewport(0, 0, targetWidth, targetHeight)

        onDrawFrame(null)
        GLES30.glFinish()

        // Read pixels from FBO
        val pixelBuffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        GLES30.glReadPixels(0, 0, targetWidth, targetHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer)
        pixelBuffer.rewind()

        // Cleanup FBO & temporary textures
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
        GLES30.glDeleteTextures(1, renderTex, 0)
        GLES30.glDeleteTextures(1, exportSrcTex, 0)

        // Restore preview state
        imageTexId = prevImageTex
        surfaceWidth = prevW
        surfaceHeight = prevH
        splitPos = origSplit
        showBefore = origShowBefore
        GLES30.glViewport(0, 0, prevW, prevH)

        if (uploadBitmap !== sourceBitmap && !uploadBitmap.isRecycled) {
            uploadBitmap.recycle()
        }

        // Create Android Bitmap and flip vertically (OpenGL Y is inverted)
        val outBmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        outBmp.copyPixelsFromBuffer(pixelBuffer)

        val matrix = android.graphics.Matrix().apply {
            postScale(1f, -1f)
        }
        val flipped = Bitmap.createBitmap(outBmp, 0, 0, targetWidth, targetHeight, matrix, true)
        if (flipped !== outBmp && !outBmp.isRecycled) {
            outBmp.recycle()
        }
        return flipped
    }

    private fun cacheUniformLocations() {
        val names = arrayOf(
            "uImage", "uLut", "uLutSize", "uLutIntensity", "uLutStage", "uShowBefore", "uSplitPos",
            "uAutoColor", "uAutoMin", "uAutoMax", "uAutoWb",
            "uExposure", "uContrast", "uHighlights", "uShadows", "uWhites", "uBlacks",
            "uTemp", "uTint", "uVibrance", "uSaturation", "uVignette",
            "uGrainIntensity", "uGrainSize", "uSkinSmooth", "uSkinRadiance",
            "uClarity", "uGlow", "uSharpness", "uResolution",
            "uHslRed", "uHslOrange", "uHslYellow", "uHslGreen",
            "uHslAqua", "uHslBlue", "uHslPurple", "uHslMagenta",
            "uBalShadows", "uBalMids", "uBalHighlights"
        )
        for (name in names) {
            locs[name] = GLES30.glGetUniformLocation(programId, name)
        }
    }

    private fun setUniform1i(name: String, v: Int) = locs[name]?.let { GLES30.glUniform1i(it, v) }
    private fun setUniform1f(name: String, v: Float) = locs[name]?.let { GLES30.glUniform1f(it, v) }
    private fun setUniform2f(name: String, v1: Float, v2: Float) = locs[name]?.let { GLES30.glUniform2f(it, v1, v2) }
    private fun setUniform3f(name: String, v1: Float, v2: Float, v3: Float) = locs[name]?.let { GLES30.glUniform3f(it, v1, v2, v3) }

    private fun readAsset(path: String): String {
        return context.assets.open(path).use {
            BufferedReader(InputStreamReader(it)).readText()
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $log")
        }
        return shader
    }
}
