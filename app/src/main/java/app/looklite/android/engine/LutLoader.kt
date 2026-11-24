package app.looklite.android.engine

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

object LutLoader {
    private val lutCache = ConcurrentHashMap<String, ByteBuffer>()

    fun loadLut(context: Context, filename: String): ByteBuffer {
        lutCache[filename]?.let {
            it.rewind()
            return it
        }

        try {
            val assetPath = "luts/$filename"
            context.assets.open(assetPath).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                var size = 17
                var line: String?

                // Read header
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.startsWith("LUT_3D_SIZE")) {
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            size = parts[1].toIntOrNull() ?: 17
                        }
                        break
                    }
                }

                val totalElements = size * size * size * 3
                val buffer = ByteBuffer.allocateDirect(totalElements).apply {
                    order(ByteOrder.nativeOrder())
                }

                // Parse RGB entries
                var count = 0
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("TITLE") ||
                        trimmed.startsWith("DOMAIN_") || trimmed.startsWith("LUT_")) {
                        continue
                    }
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        val r = (parts[0].toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
                        val g = (parts[1].toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
                        val b = (parts[2].toFloatOrNull() ?: 0f).coerceIn(0f, 1f)

                        buffer.put((r * 255f + 0.5f).toInt().toByte())
                        buffer.put((g * 255f + 0.5f).toInt().toByte())
                        buffer.put((b * 255f + 0.5f).toInt().toByte())
                        count += 3
                        if (count >= totalElements) break
                    }
                }

                buffer.rewind()
                lutCache[filename] = buffer
                return buffer
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return createIdentityLut(17)
        }
    }

    fun createIdentityLut(size: Int = 17): ByteBuffer {
        val totalElements = size * size * size * 3
        val buffer = ByteBuffer.allocateDirect(totalElements).apply {
            order(ByteOrder.nativeOrder())
        }
        val step = 1.0f / (size - 1)
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    buffer.put(((r * step) * 255f + 0.5f).toInt().toByte())
                    buffer.put(((g * step) * 255f + 0.5f).toInt().toByte())
                    buffer.put(((b * step) * 255f + 0.5f).toInt().toByte())
                }
            }
        }
        buffer.rewind()
        return buffer
    }
}
