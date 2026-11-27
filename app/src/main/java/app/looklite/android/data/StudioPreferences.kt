package app.looklite.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.looklite.android.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "looklite_studio")

class StudioPreferences(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val CUSTOM_LOOKS = stringPreferencesKey("custom_looks_json")
        val FRAME_TEMPLATES = stringPreferencesKey("frame_templates_json")
    }

    val languageFlow: Flow<AppLanguageOption> = context.studioDataStore.data.map { prefs ->
        when (prefs[Keys.LANGUAGE]) {
            AppLanguageOption.ENGLISH.tag -> AppLanguageOption.ENGLISH
            AppLanguageOption.INDONESIAN.tag -> AppLanguageOption.INDONESIAN
            else -> AppLanguageOption.SYSTEM
        }
    }

    val customLooksFlow: Flow<List<CustomLook>> = context.studioDataStore.data.map { prefs ->
        parseCustomLooks(prefs[Keys.CUSTOM_LOOKS])
    }

    val frameTemplatesFlow: Flow<List<CustomFrameTemplate>> = context.studioDataStore.data.map { prefs ->
        parseFrameTemplates(prefs[Keys.FRAME_TEMPLATES])
    }

    suspend fun setLanguage(option: AppLanguageOption) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = option.tag
        }
    }

    suspend fun saveCustomLooks(looks: List<CustomLook>) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_LOOKS] = serializeCustomLooks(looks)
        }
    }

    suspend fun saveFrameTemplates(templates: List<CustomFrameTemplate>) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.FRAME_TEMPLATES] = serializeFrameTemplates(templates)
        }
    }

    private fun serializeCustomLooks(looks: List<CustomLook>): String {
        val arr = JSONArray()
        for (look in looks) {
            val s = look.sliders
            val slidersObj = JSONObject().apply {
                put("exposure", s.exposure.toDouble())
                put("contrast", s.contrast.toDouble())
                put("highlights", s.highlights.toDouble())
                put("shadows", s.shadows.toDouble())
                put("whites", s.whites.toDouble())
                put("blacks", s.blacks.toDouble())
                put("temperature", s.temperature.toDouble())
                put("tint", s.tint.toDouble())
                put("vibrance", s.vibrance.toDouble())
                put("saturation", s.saturation.toDouble())
                put("vignette", s.vignette.toDouble())
                put("grainIntensity", s.grainIntensity.toDouble())
                put("grainSize", s.grainSize.toDouble())
                put("skinSmooth", s.skinSmooth.toDouble())
                put("skinRadiance", s.skinRadiance.toDouble())
                put("naturalSkin", s.naturalSkin.toDouble())
                put("clarity", s.clarity.toDouble())
                put("glow", s.glow.toDouble())
                put("sharpness", s.sharpness.toDouble())
            }
            val obj = JSONObject().apply {
                put("id", look.id)
                put("name", look.name)
                put("basePresetId", look.basePresetId)
                put("lutFile", look.lutFile)
                put("lutStage", look.lutStage)
                put("intensity", look.intensity)
                put("autoColor", look.autoColor)
                put("sliders", slidersObj)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseCustomLooks(raw: String?): List<CustomLook> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val sObj = obj.optJSONObject("sliders") ?: JSONObject()
                    val sliders = SliderValues(
                        exposure = sObj.optDouble("exposure", 0.0).toFloat(),
                        contrast = sObj.optDouble("contrast", 0.0).toFloat(),
                        highlights = sObj.optDouble("highlights", 0.0).toFloat(),
                        shadows = sObj.optDouble("shadows", 0.0).toFloat(),
                        whites = sObj.optDouble("whites", 0.0).toFloat(),
                        blacks = sObj.optDouble("blacks", 0.0).toFloat(),
                        temperature = sObj.optDouble("temperature", 0.0).toFloat(),
                        tint = sObj.optDouble("tint", 0.0).toFloat(),
                        vibrance = sObj.optDouble("vibrance", 0.0).toFloat(),
                        saturation = sObj.optDouble("saturation", 0.0).toFloat(),
                        vignette = sObj.optDouble("vignette", 0.0).toFloat(),
                        grainIntensity = sObj.optDouble("grainIntensity", 0.0).toFloat(),
                        grainSize = sObj.optDouble("grainSize", 10.0).toFloat(),
                        skinSmooth = sObj.optDouble("skinSmooth", 0.0).toFloat(),
                        skinRadiance = sObj.optDouble("skinRadiance", 0.0).toFloat(),
                        naturalSkin = sObj.optDouble("naturalSkin", 0.0).toFloat(),
                        clarity = sObj.optDouble("clarity", 0.0).toFloat(),
                        glow = sObj.optDouble("glow", 0.0).toFloat(),
                        sharpness = sObj.optDouble("sharpness", 0.0).toFloat()
                    )
                    add(
                        CustomLook(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            basePresetId = obj.optString("basePresetId", ""),
                            lutFile = obj.optString("lutFile", ""),
                            lutStage = obj.optString("lutStage", "early"),
                            intensity = obj.optInt("intensity", 100),
                            autoColor = obj.optBoolean("autoColor", false),
                            sliders = sliders
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeFrameTemplates(templates: List<CustomFrameTemplate>): String {
        val arr = JSONArray()
        for (tpl in templates) {
            val obj = JSONObject().apply {
                put("id", tpl.id)
                put("name", tpl.name)
                put("style", tpl.settings.style.id)
                put("paddingFraction", tpl.settings.paddingFraction.toDouble())
                put("signatureText", tpl.settings.signatureText)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseFrameTemplates(raw: String?): List<CustomFrameTemplate> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val styleId = obj.optString("style", "white")
                    val style = FrameStyle.values().firstOrNull { it.id == styleId } ?: FrameStyle.WHITE
                    add(
                        CustomFrameTemplate(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            settings = FrameSettings(
                                style = style,
                                paddingFraction = obj.optDouble("paddingFraction", 0.04).toFloat(),
                                signatureText = obj.optString("signatureText", "")
                            )
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
