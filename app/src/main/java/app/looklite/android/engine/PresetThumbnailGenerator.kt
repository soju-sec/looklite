package app.looklite.android.engine

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.looklite.android.model.SliderValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.pow

object PresetThumbnailGenerator {

    private const val THUMB_W = 102
    private const val THUMB_H = 132

    /**
     * Center-crops and scales the active photo once into a tiny working buffer
     * matching the 68.dp x 88.dp thumbnail aspect ratio.
     */
    fun createBaseThumbnail(source: Bitmap): IntArray {
        val targetAspect = THUMB_W.toFloat() / THUMB_H.toFloat()
        val srcW = source.width
        val srcH = source.height
        val srcAspect = srcW.toFloat() / srcH.toFloat()

        val cropW: Int
        val cropH: Int
        val cropX: Int
        val cropY: Int
        if (srcAspect > targetAspect) {
            cropH = srcH
            cropW = (cropH * targetAspect).toInt().coerceAtMost(srcW)
            cropX = (srcW - cropW) / 2
            cropY = 0
        } else {
            cropW = srcW
            cropH = (cropW / targetAspect).toInt().coerceAtMost(srcH)
            cropX = 0
            cropY = (srcH - cropH) / 2
        }

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(cropped, THUMB_W, THUMB_H, true)
        val pixels = IntArray(THUMB_W * THUMB_H)
        scaled.getPixels(pixels, 0, THUMB_W, 0, 0, THUMB_W, THUMB_H)
        if (cropped !== source && !cropped.isRecycled) cropped.recycle()
        if (scaled !== cropped && !scaled.isRecycled) scaled.recycle()
        return pixels
    }

    suspend fun renderThumbnail(
        context: Context,
        basePixels: IntArray,
        lutFile: String,
        sliders: SliderValues,
        intensity: Float = 1f
    ): ImageBitmap = withContext(Dispatchers.Default) {
        val loaded = LutLoader.loadLut(context, lutFile)
        val lutSize = loaded.size
        val lutBytes = ByteArray(loaded.buffer.remaining())
        loaded.buffer.duplicate().get(lutBytes)

        val outPixels = IntArray(basePixels.size)
        val expGain = 2f.pow(sliders.exposure * 0.55f)
        val contrastFactor = 1f + (sliders.contrast / 100f) * 0.45f
        val tempShift = (sliders.temperature / 100f) * 0.14f
        val tintShift = (sliders.tint / 100f) * 0.10f
        val satFactor = (1f + sliders.saturation / 100f).coerceIn(0f, 2f)
        val maxIdx = lutSize - 1

        for (i in basePixels.indices) {
            val px = basePixels[i]
            var r = ((px ushr 16) and 0xFF) / 255f
            var g = ((px ushr 8) and 0xFF) / 255f
            var b = (px and 0xFF) / 255f

            val origR = r
            val origG = g
            val origB = b

            // Trilinear 3D LUT lookup
            val rx = (r * maxIdx).coerceIn(0f, maxIdx.toFloat())
            val gx = (g * maxIdx).coerceIn(0f, maxIdx.toFloat())
            val bx = (b * maxIdx).coerceIn(0f, maxIdx.toFloat())

            val r0 = rx.toInt().coerceAtMost(maxIdx - 1)
            val g0 = gx.toInt().coerceAtMost(maxIdx - 1)
            val b0 = bx.toInt().coerceAtMost(maxIdx - 1)
            val r1 = r0 + 1
            val g1 = g0 + 1
            val b1 = b0 + 1

            val fr = rx - r0
            val fg = gx - g0
            val fb = bx - b0

            fun sampleLut(ri: Int, gi: Int, bi: Int): Int {
                return ((bi * lutSize + gi) * lutSize + ri) * 3
            }

            val i000 = sampleLut(r0, g0, b0)
            val i100 = sampleLut(r1, g0, b0)
            val i010 = sampleLut(r0, g1, b0)
            val i110 = sampleLut(r1, g1, b0)
            val i001 = sampleLut(r0, g0, b1)
            val i101 = sampleLut(r1, g0, b1)
            val i011 = sampleLut(r0, g1, b1)
            val i111 = sampleLut(r1, g1, b1)

            fun interpChannel(ch: Int): Float {
                val c000 = (lutBytes[i000 + ch].toInt() and 0xFF) / 255f
                val c100 = (lutBytes[i100 + ch].toInt() and 0xFF) / 255f
                val c010 = (lutBytes[i010 + ch].toInt() and 0xFF) / 255f
                val c110 = (lutBytes[i110 + ch].toInt() and 0xFF) / 255f
                val c001 = (lutBytes[i001 + ch].toInt() and 0xFF) / 255f
                val c101 = (lutBytes[i101 + ch].toInt() and 0xFF) / 255f
                val c011 = (lutBytes[i011 + ch].toInt() and 0xFF) / 255f
                val c111 = (lutBytes[i111 + ch].toInt() and 0xFF) / 255f

                val c00 = c000 + (c100 - c000) * fr
                val c10 = c010 + (c110 - c010) * fr
                val c01 = c001 + (c101 - c001) * fr
                val c11 = c011 + (c111 - c011) * fr
                val c0 = c00 + (c10 - c00) * fg
                val c1 = c01 + (c11 - c01) * fg
                return c0 + (c1 - c0) * fb
            }

            r = interpChannel(0)
            g = interpChannel(1)
            b = interpChannel(2)

            // Mix LUT by intensity
            if (intensity < 0.999f) {
                r = origR + (r - origR) * intensity
                g = origG + (g - origG) * intensity
                b = origB + (b - origB) * intensity
            }

            // Key tone & WB adjustments
            r = (r * expGain + tempShift).coerceIn(0f, 1f)
            g = (g * expGain - tintShift).coerceIn(0f, 1f)
            b = (b * expGain - tempShift).coerceIn(0f, 1f)

            r = ((r - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
            g = ((g - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
            b = ((b - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)

            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = (luma + (r - luma) * satFactor).coerceIn(0f, 1f)
            g = (luma + (g - luma) * satFactor).coerceIn(0f, 1f)
            b = (luma + (b - luma) * satFactor).coerceIn(0f, 1f)

            val ri = (r * 255f).toInt().coerceIn(0, 255)
            val gi = (g * 255f).toInt().coerceIn(0, 255)
            val bi = (b * 255f).toInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val bmp = Bitmap.createBitmap(outPixels, THUMB_W, THUMB_H, Bitmap.Config.ARGB_8888)
        bmp.asImageBitmap()
    }
}
