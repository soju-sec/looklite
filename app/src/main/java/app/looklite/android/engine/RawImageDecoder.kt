package app.looklite.android.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

data class DecodedPhoto(
    val bitmap: Bitmap,
    val isRaw: Boolean
)

object RawImageDecoder {

    private const val MAX_DIM = 4096
    private val RAW_EXTENSIONS = setOf(
        "dng", "raw", "cr2", "cr3", "nef", "arw", "orf", "raf", "rw2", "pef", "srw"
    )

    fun decode(context: Context, uri: Uri): DecodedPhoto? {
        val isRaw = isRawUri(context, uri)

        // 1. Android 9+ ImageDecoder natively decodes DNG (via libdng_sdk/SkRawCodec) + HEIC/JPG/PNG/WEBP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > MAX_DIM) {
                        val scale = MAX_DIM.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
                val softwareBmp = ensureArgb8888(decoded)
                return DecodedPhoto(softwareBmp, isRaw)
            } catch (_: Throwable) {
                // Fall through to BitmapFactory / embedded JPEG extraction
            }
        }

        // 2. Standard BitmapFactory + EXIF orientation
        decodeViaBitmapFactory(context, uri)?.let { bmp ->
            return DecodedPhoto(ensureArgb8888(bmp), isRaw)
        }

        // 3. Embedded full-res JPEG extraction for proprietary camera RAWs (.cr2, .nef, .arw, .raf, etc.)
        if (isRaw) {
            extractLargestEmbeddedJpeg(context, uri)?.let { bmp ->
                val oriented = applyExifOrientation(context, uri, bmp)
                return DecodedPhoto(ensureArgb8888(oriented), true)
            }
        }

        return null
    }

    private fun isRawUri(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
        if (mime.contains("dng") || mime.contains("raw") || mime.contains("cr2") ||
            mime.contains("nef") || mime.contains("arw") || mime.contains("raf")
        ) {
            return true
        }
        val name = getDisplayName(context, uri)?.lowercase() ?: uri.lastPathSegment?.lowercase() ?: ""
        val ext = name.substringAfterLast('.', "")
        return ext in RAW_EXTENSIONS
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeViaBitmapFactory(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / sampleSize > MAX_DIM) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            applyExifOrientation(context, uri, original)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Scans TIFF-based RAW containers for embedded JPEG streams (SOI 0xFF 0xD8 0xFF .. EOI 0xFF 0xD9)
     * and decodes the largest valid preview image.
     */
    private fun extractLargestEmbeddedJpeg(context: Context, uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val raw = input.readBytes()
                if (raw.size > 90 * 1024 * 1024) return null else raw
            } ?: return null

            var bestBytes: ByteArray? = null
            var i = 0
            val len = bytes.size
            while (i < len - 3) {
                if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte() && bytes[i + 2] == 0xFF.toByte()) {
                    val start = i
                    var j = i + 3
                    var foundEnd = -1
                    while (j < len - 1) {
                        if (bytes[j] == 0xFF.toByte() && bytes[j + 1] == 0xD9.toByte()) {
                            foundEnd = j + 2
                            break
                        }
                        j++
                    }
                    if (foundEnd > start) {
                        val size = foundEnd - start
                        if (size > 32_768 && (bestBytes == null || size > bestBytes.size)) {
                            bestBytes = bytes.copyOfRange(start, foundEnd)
                        }
                        i = foundEnd
                        continue
                    }
                }
                i++
            }

            bestBytes?.let { jpg ->
                BitmapFactory.decodeByteArray(jpg, 0, jpg.size)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }
}
