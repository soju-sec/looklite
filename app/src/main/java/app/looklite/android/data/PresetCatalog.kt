package app.looklite.android.data

import android.content.Context
import app.looklite.android.R
import app.looklite.android.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object PresetCatalog {
    private var cachedPresets: List<PresetProfile>? = null

    val swatchMap = mapOf(
        "poles.fnatix_flash" to Pair(0xFF1A2A3A, 0xFFE0B48A),
        "poles.korean_porcelain" to Pair(0xFFDBE2EF, 0xFFF7D6BF),
        "poles.emerald_moody" to Pair(0xFF0E2A22, 0xFFB8860B),
        "poles.night_ocean" to Pair(0xFF0F2027, 0xFF203A43),
        "poles.soft_glow_dream" to Pair(0xFFFBC2EB, 0xFFA6C1EE),
        "poles.studio_flash" to Pair(0xFF2C3E50, 0xFFBDC3C7),
        "poles.golden_radiance" to Pair(0xFFFF8C00, 0xFFFFD700),
        "poles.dark_aesthetic" to Pair(0xFF141414, 0xFF434343),

        "meitu.cream_skin" to Pair(0xFFF6D5C8, 0xFFFFF1E6),
        "meitu.cool_white" to Pair(0xFFD8E6F8, 0xFFFDF2F8),
        "meitu.natural_clear" to Pair(0xFFA8D8EA, 0xFFF7E8D8),
        "meitu.pearl_violet" to Pair(0xFFC5B4E3, 0xFFF6D6E8),
        "meitu.peach_milk" to Pair(0xFFFFB5A7, 0xFFFCD5CE),
        "meitu.airy_fresh" to Pair(0xFF9ED5D6, 0xFFEEF5F6),
        "meitu.glass_sea" to Pair(0xFF1CB5E0, 0xFF8CE0EA),
        "meitu.editorial_dim" to Pair(0xFF23252E, 0xFF6B6E7A),

        "fuji.classic_chrome" to Pair(0xFF2C3848, 0xFF9B8270),
        "fuji.classic_neg" to Pair(0xFF324338, 0xFFB86242),
        "fuji.velvia_50" to Pair(0xFF1B5E20, 0xFFD81B60),
        "fuji.provia_100f" to Pair(0xFF37474F, 0xFF78909C),
        "fuji.astia" to Pair(0xFFD38B72, 0xFFDFA996),
        "fuji.reala_ace" to Pair(0xFF2E7D32, 0xFFFBC02D),
        "fuji.eterna" to Pair(0xFF455A64, 0xFFA1887F),
        "fuji.nostalgic_neg" to Pair(0xFF795548, 0xFFD7CCC8),
        "fuji.acros" to Pair(0xFF111111, 0xFF888888),
        "fuji.bleach_bypass" to Pair(0xFF212121, 0xFFCFD8DC),

        "kodak.portra_400" to Pair(0xFFDFA97B, 0xFFA66A46),
        "kodak.portra_160" to Pair(0xFFE4BE9E, 0xFFC28864),
        "kodak.portra_800" to Pair(0xFFE67E22, 0xFF995010),
        "kodak.gold_200" to Pair(0xFFF39C12, 0xFFD35400),
        "kodak.ektar_100" to Pair(0xFF0D47A1, 0xFFB71C1C),
        "kodak.kodachrome_64" to Pair(0xFFE65100, 0xFFFFB300),
        "kodak.cinestill_800t" to Pair(0xFF00838F, 0xFFE91E63),
        "kodak.vision3_500t" to Pair(0xFF1A237E, 0xFFFF6F00),
        "kodak.trix_400" to Pair(0xFF050505, 0xFF555555),

        "leica.classic" to Pair(0xFF263238, 0xFFB0BEC5),
        "leica.natural" to Pair(0xFF37474F, 0xFFECEFF1),
        "leica.vivid" to Pair(0xFFC2185B, 0xFF0097A7),
        "leica.teal" to Pair(0xFF004D40, 0xFFFF8A65),
        "leica.monochrom" to Pair(0xFF141414, 0xFF757575),
        "leica.greg_williams" to Pair(0xFF0D0D0D, 0xFFBDBDBD),

        "hasselblad.hncs_standard" to Pair(0xFF2B313A, 0xFFCFD8DC),
        "hasselblad.hncs_portrait" to Pair(0xFF37474F, 0xFFF0D5C0),
        "hasselblad.hncs_landscape" to Pair(0xFF1B382B, 0xFF81C784),
        "hasselblad.hncs_monochrome" to Pair(0xFF121212, 0xFFCFD8DC),

        "ilford.hp5_plus" to Pair(0xFF1C1C1C, 0xFF9E9E9E),
        "vintage.disposable_400" to Pair(0xFF5D4037, 0xFFFFCC80),
        "vintage.polaroid_color" to Pair(0xFF455A64, 0xFFB0BEC5),
        "vintage.golden_light" to Pair(0xFFE65100, 0xFFFFE082),
        "vintage.street_car" to Pair(0xFF006064, 0xFFBF360C)
    )

    fun getAllPresets(context: Context): List<PresetProfile> {
        cachedPresets?.let { return it }

        val list = mutableListOf<PresetProfile>()
        try {
            val jsonString = context.assets.open("presets.json").use {
                BufferedReader(InputStreamReader(it)).readText()
            }
            val arr = JSONArray(jsonString)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("preset_id")
                val brandStr = obj.optString("camera_system", "poles")
                val brand = when (brandStr.lowercase()) {
                    "beauty", "meitu" -> CameraBrand.BEAUTY
                    "fuji", "fujifilm" -> CameraBrand.FUJIFILM
                    "kodak" -> CameraBrand.KODAK
                    "leica" -> CameraBrand.LEICA
                    "hasselblad" -> CameraBrand.HASSELBLAD
                    "ilford", "vintage" -> CameraBrand.VINTAGE
                    else -> CameraBrand.POLES
                }
                val name = obj.optString("display_label", obj.optString("look_name", id))
                val tag = obj.optString("tag", "")
                val lutAsset = obj.optString("lut_asset_path", "luts/$id.cube")
                val lutFilename = if (lutAsset.contains("/")) lutAsset.substringAfterLast("/") else lutAsset
                val lutStage = obj.optString("lut_stage", "early")

                val slidersObj = obj.optJSONObject("default_sliders") ?: JSONObject()
                val sliders = parseSliderValues(slidersObj)

                val swatch = swatchMap[id] ?: Pair(0xFF2B4C6F, 0xFFF4B251)

                list.add(
                    PresetProfile(
                        id = id,
                        name = name,
                        brand = brand,
                        description = tag,
                        lutFile = lutFilename,
                        lutStage = lutStage,
                        defaultSliders = sliders,
                        gradientColors = swatch
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedPresets = list
        return list
    }

    private fun parseSliderValues(obj: JSONObject): SliderValues {
        val hslObj = obj.optJSONObject("hsl") ?: JSONObject()
        val hslChannels = HslChannels(
            red = parseHslVector(hslObj.optJSONObject("red")),
            orange = parseHslVector(hslObj.optJSONObject("orange")),
            yellow = parseHslVector(hslObj.optJSONObject("yellow")),
            green = parseHslVector(hslObj.optJSONObject("green")),
            aqua = parseHslVector(hslObj.optJSONObject("aqua")),
            blue = parseHslVector(hslObj.optJSONObject("blue")),
            purple = parseHslVector(hslObj.optJSONObject("purple")),
            magenta = parseHslVector(hslObj.optJSONObject("magenta"))
        )

        val cbObj = obj.optJSONObject("color_balance") ?: JSONObject()
        val colorBalance = ColorBalance(
            shadows = parseBalVector(cbObj.optJSONObject("shadows")),
            midtones = parseBalVector(cbObj.optJSONObject("midtones")),
            highlights = parseBalVector(cbObj.optJSONObject("highlights"))
        )

        return SliderValues(
            exposure = obj.optDouble("exposure", 0.0).toFloat(),
            contrast = obj.optDouble("contrast", 0.0).toFloat(),
            highlights = obj.optDouble("highlights", 0.0).toFloat(),
            shadows = obj.optDouble("shadows", 0.0).toFloat(),
            whites = obj.optDouble("whites", 0.0).toFloat(),
            blacks = obj.optDouble("blacks", 0.0).toFloat(),
            temperature = obj.optDouble("temp", 0.0).toFloat(),
            tint = obj.optDouble("tint", 0.0).toFloat(),
            vibrance = obj.optDouble("vibrance", 0.0).toFloat(),
            saturation = obj.optDouble("saturation", 0.0).toFloat(),
            vignette = obj.optDouble("vignette", 0.0).toFloat(),
            grainIntensity = obj.optDouble("grain_intensity", 0.0).toFloat(),
            grainSize = obj.optDouble("grain_size", 1.0).toFloat(),
            skinSmooth = obj.optDouble("skin_smooth", 0.0).toFloat(),
            skinRadiance = obj.optDouble("skin_radiance", 0.0).toFloat(),
            clarity = obj.optDouble("clarity", 0.0).toFloat(),
            glow = obj.optDouble("glow", 0.0).toFloat(),
            sharpness = obj.optDouble("sharpness", 0.0).toFloat(),
            hsl = hslChannels,
            colorBalance = colorBalance
        )
    }

    private fun parseHslVector(obj: JSONObject?): HslVector {
        if (obj == null) return HslVector()
        return HslVector(
            h = obj.optDouble("h", 0.0).toFloat(),
            s = obj.optDouble("s", 0.0).toFloat(),
            l = obj.optDouble("l", 0.0).toFloat()
        )
    }

    private fun parseBalVector(obj: JSONObject?): FloatArray {
        if (obj == null) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(
            obj.optDouble("r", 0.0).toFloat(),
            obj.optDouble("g", 0.0).toFloat(),
            obj.optDouble("b", 0.0).toFloat()
        )
    }

    val aspectRatios = listOf(
        AspectRatioOption("original", R.string.ratio_original, "Original", null, null),
        AspectRatioOption("1:1", R.string.ratio_square, "1:1", "1:1", 1.0f),
        AspectRatioOption("4:5", R.string.ratio_feed, "4:5", "4:5", 4f / 5f),
        AspectRatioOption("9:16", R.string.ratio_story, "9:16", "9:16", 9f / 16f),
        AspectRatioOption("16:9", R.string.ratio_cinema, "16:9", "16:9", 16f / 9f),
        AspectRatioOption("3:2", R.string.ratio_photo, "3:2", "3:2", 3f / 2f)
    )
}
