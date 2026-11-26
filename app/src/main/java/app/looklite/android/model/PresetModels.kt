package app.looklite.android.model

enum class CameraBrand(val id: String, val displayName: String) {
    POLES("poles", "POLES"),
    BEAUTY("beauty", "BEAUTY"),
    FUJIFILM("fuji", "FUJI"),
    KODAK("kodak", "KODAK"),
    LEICA("leica", "LEICA"),
    HASSELBLAD("hasselblad", "HASSELBLAD"),
    VINTAGE("vintage", "ILFORD")
}

data class ColorBalance(
    val shadows: FloatArray = floatArrayOf(0f, 0f, 0f),
    val midtones: FloatArray = floatArrayOf(0f, 0f, 0f),
    val highlights: FloatArray = floatArrayOf(0f, 0f, 0f)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ColorBalance
        return shadows.contentEquals(other.shadows) &&
               midtones.contentEquals(other.midtones) &&
               highlights.contentEquals(other.highlights)
    }

    override fun hashCode(): Int {
        var result = shadows.contentHashCode()
        result = 31 * result + midtones.contentHashCode()
        result = 31 * result + highlights.contentHashCode()
        return result
    }
}

data class HslVector(val h: Float = 0f, val s: Float = 0f, val l: Float = 0f)

data class HslChannels(
    val red: HslVector = HslVector(),
    val orange: HslVector = HslVector(),
    val yellow: HslVector = HslVector(),
    val green: HslVector = HslVector(),
    val aqua: HslVector = HslVector(),
    val blue: HslVector = HslVector(),
    val purple: HslVector = HslVector(),
    val magenta: HslVector = HslVector()
)

data class SliderValues(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    val vignette: Float = 0f,
    val grainIntensity: Float = 0f,
    val grainSize: Float = 1.0f,
    val skinSmooth: Float = 0f,
    val skinRadiance: Float = 0f,
    val clarity: Float = 0f,
    val glow: Float = 0f,
    val sharpness: Float = 0f,
    val hsl: HslChannels = HslChannels(),
    val colorBalance: ColorBalance = ColorBalance()
)

data class PresetProfile(
    val id: String,
    val name: String,
    val brand: CameraBrand,
    val description: String,
    val lutFile: String,
    val lutStage: String = "early",
    val defaultSliders: SliderValues = SliderValues(),
    val gradientColors: Pair<Long, Long> = Pair(0xFF2B4C6F, 0xFFF4B251)
)

data class AspectRatioOption(
    val id: String,
    val labelResId: Int = 0,
    val label: String,
    val badge: String? = null,
    val ratio: Float? = null // null means Original
)

enum class LookCategory {
    MY_LOOKS,
    KODAK,
    FUJI,
    LEICA,
    HASSELBLAD,
    ILFORD,
    RETOUCH,
    BEAUTY
}

data class CustomLook(
    val id: String,
    val name: String,
    val basePresetId: String,
    val lutFile: String,
    val lutStage: String,
    val intensity: Int,
    val autoColor: Boolean,
    val sliders: SliderValues
)

enum class FrameStyle(val id: String) {
    NONE("none"),
    WHITE("white"),
    DARK("dark"),
    POLAROID("polaroid")
}

data class FrameSettings(
    val style: FrameStyle = FrameStyle.NONE,
    val paddingFraction: Float = 0.04f,
    val signatureText: String = ""
)

data class CustomFrameTemplate(
    val id: String,
    val name: String,
    val settings: FrameSettings
)

enum class AppLanguageOption(val tag: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    INDONESIAN("in")
}
