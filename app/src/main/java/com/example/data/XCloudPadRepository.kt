package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ControlElementId
import com.example.model.ControlTransform
import com.example.model.ControllerProfile
import com.example.model.GyroMode
import com.example.model.SensitivityCurve
import com.example.model.StickMode
import com.example.model.StreamEngineSettings
import com.example.model.TvAspectRatioMode
import com.example.model.UpscalerTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xcloud_pad_store")

class XCloudPadRepository(private val context: Context) {

    private val KEY_STREAM_SETTINGS = stringPreferencesKey("stream_engine_settings_json")
    private val KEY_PROFILES_LIST = stringPreferencesKey("controller_profiles_json")
    private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    private val KEY_GM_KV_MAP = stringPreferencesKey("gm_userscript_kv_json")

    val gmMemoryCache = ConcurrentHashMap<String, String>()

    val streamSettingsFlow: Flow<StreamEngineSettings> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_STREAM_SETTINGS]
        if (raw.isNullOrBlank()) StreamEngineSettings() else parseStreamSettings(raw)
    }

    val profilesFlow: Flow<List<ControllerProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_PROFILES_LIST]
        if (raw.isNullOrBlank()) defaultProfiles() else parseProfiles(raw).ifEmpty { defaultProfiles() }
    }

    val activeProfileIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PROFILE_ID] ?: "default"
    }

    suspend fun saveStreamSettings(settings: StreamEngineSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STREAM_SETTINGS] = serializeStreamSettings(settings).toString()
        }
        gmMemoryCache["targetResolution"] = JSONObject.quote(settings.targetResolution)
        gmMemoryCache["maxBitrateKbps"] = settings.maxBitrateKbps.toString()
        gmMemoryCache["codecProfile"] = JSONObject.quote(settings.codecProfile)
        gmMemoryCache["showStatsOverlay"] = settings.showStatsOverlay.toString()
        gmMemoryCache["clarityBoost"] = settings.clarityBoost.toString()
        gmMemoryCache["videoSaturation"] = settings.videoSaturation.toString()
        gmMemoryCache["videoContrast"] = settings.videoContrast.toString()
        gmMemoryCache["videoBrightness"] = settings.videoBrightness.toString()
        gmMemoryCache["videoSharpness"] = settings.videoSharpness.toString()
        gmMemoryCache["enableFsr"] = settings.enableFsr.toString()
        gmMemoryCache["fsrSharpness"] = settings.fsrSharpness.toString()
        gmMemoryCache["upscalerTier"] = JSONObject.quote(settings.upscalerTier.name)
        gmMemoryCache["upscalerDetailStrength"] = settings.upscalerDetailStrength.toString()
        gmMemoryCache["upscalerDeblockStrength"] = settings.upscalerDeblockStrength.toString()
        gmMemoryCache["upscalerGrainEnabled"] = settings.upscalerGrainEnabled.toString()
        gmMemoryCache["upscalerTestPattern"] = settings.upscalerTestPattern.toString()
        gmMemoryCache["upscalerOutputWidth"] = settings.upscalerOutputWidth.toString()
        gmMemoryCache["upscalerOutputHeight"] = settings.upscalerOutputHeight.toString()
        gmMemoryCache["audioBoostPercent"] = settings.audioBoostPercent.toString()
        gmMemoryCache["disableIdleCheck"] = settings.disableIdleCheck.toString()
        gmMemoryCache["enableDashboardGamepadNav"] = settings.enableDashboardGamepadNav.toString()
        gmMemoryCache["videoAspectMode"] = JSONObject.quote(settings.tvAspectRatioMode.key)
    }

    suspend fun saveProfiles(profiles: List<ControllerProfile>, activeId: String) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            profiles.forEach { arr.put(serializeProfile(it)) }
            prefs[KEY_PROFILES_LIST] = arr.toString()
            prefs[KEY_ACTIVE_PROFILE_ID] = activeId
        }
    }

    suspend fun setGmValue(key: String, rawJsonValue: String) {
        gmMemoryCache[key] = rawJsonValue
        context.dataStore.edit { prefs ->
            val obj = JSONObject(prefs[KEY_GM_KV_MAP] ?: "{}")
            obj.put(key, rawJsonValue)
            prefs[KEY_GM_KV_MAP] = obj.toString()
        }
    }

    suspend fun deleteGmValue(key: String) {
        gmMemoryCache.remove(key)
        context.dataStore.edit { prefs ->
            val obj = JSONObject(prefs[KEY_GM_KV_MAP] ?: "{}")
            obj.remove(key)
            prefs[KEY_GM_KV_MAP] = obj.toString()
        }
    }

    fun defaultProfiles(): List<ControllerProfile> = listOf(
        ControllerProfile(
            id = "default",
            name = "Standard Crystal Pad",
            gameTitleMatch = "",
            stickMode = StickMode.FLOATING,
            deadzone = 0.08f,
            sensitivity = 1.0f,
            sensitivityCurve = SensitivityCurve.LINEAR
        )
    )

    fun serializeStreamSettings(s: StreamEngineSettings): JSONObject = JSONObject().apply {
        put("targetResolution", s.targetResolution)
        put("maxBitrateKbps", s.maxBitrateKbps)
        put("codecProfile", s.codecProfile)
        put("showStatsOverlay", s.showStatsOverlay)
        put("clarityBoost", s.clarityBoost)
        put("videoSaturation", s.videoSaturation)
        put("videoContrast", s.videoContrast)
        put("videoBrightness", s.videoBrightness)
        put("videoSharpness", s.videoSharpness)
        put("enableFsr", s.enableFsr)
        put("fsrSharpness", s.fsrSharpness)
        put("upscalerTier", s.upscalerTier.name)
        put("upscalerDetailStrength", s.upscalerDetailStrength)
        put("upscalerDeblockStrength", s.upscalerDeblockStrength)
        put("upscalerGrainEnabled", s.upscalerGrainEnabled)
        put("upscalerTestPattern", s.upscalerTestPattern)
        put("upscalerOutputWidth", s.upscalerOutputWidth)
        put("upscalerOutputHeight", s.upscalerOutputHeight)
        put("audioBoostPercent", s.audioBoostPercent)
        put("disableIdleCheck", s.disableIdleCheck)
        put("enableDashboardGamepadNav", s.enableDashboardGamepadNav)
        put("videoAspectMode", s.tvAspectRatioMode.key)
    }

    private fun parseStreamSettings(raw: String): StreamEngineSettings = try {
        val o = JSONObject(raw)
        val aspectKey = o.optString("videoAspectMode", "fit")
        val mode = TvAspectRatioMode.entries.firstOrNull { it.key == aspectKey } ?: TvAspectRatioMode.FIT
        StreamEngineSettings(
            targetResolution = o.optString("targetResolution", "1080p_hq"),
            maxBitrateKbps = o.optInt("maxBitrateKbps", 20000),
            codecProfile = o.optString("codecProfile", "high"),
            showStatsOverlay = o.optBoolean("showStatsOverlay", true),
            clarityBoost = o.optInt("clarityBoost", 1),
            videoSaturation = o.optInt("videoSaturation", 105),
            videoContrast = o.optInt("videoContrast", 103),
            videoBrightness = o.optInt("videoBrightness", 100),
            videoSharpness = o.optInt("videoSharpness", 0),
            enableFsr = o.optBoolean("enableFsr", false),
            fsrSharpness = o.optInt("fsrSharpness", 75),
            upscalerTier = runCatching { UpscalerTier.valueOf(o.optString("upscalerTier", "AUTO")) }.getOrDefault(UpscalerTier.AUTO),
            upscalerDetailStrength = o.optInt("upscalerDetailStrength", 40),
            upscalerDeblockStrength = o.optInt("upscalerDeblockStrength", 20),
            upscalerGrainEnabled = o.optBoolean("upscalerGrainEnabled", false),
            upscalerTestPattern = o.optBoolean("upscalerTestPattern", false),
            upscalerOutputWidth = o.optInt("upscalerOutputWidth", 3840),
            upscalerOutputHeight = o.optInt("upscalerOutputHeight", 2160),
            audioBoostPercent = o.optInt("audioBoostPercent", 100),
            disableIdleCheck = o.optBoolean("disableIdleCheck", true),
            enableDashboardGamepadNav = o.optBoolean("enableDashboardGamepadNav", true),
            tvAspectRatioMode = mode
        )
    } catch (_: Exception) { StreamEngineSettings() }

    private fun serializeProfile(p: ControllerProfile): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("gameTitleMatch", p.gameTitleMatch)
        put("stickMode", p.stickMode.name)
        put("deadzone", p.deadzone.toDouble())
        put("sensitivity", p.sensitivity.toDouble())
        put("sensitivityCurve", p.sensitivityCurve.name)
        put("slideBetweenButtons", p.slideBetweenButtons)
        put("doubleTapStickClick", p.doubleTapStickClick)
        put("hapticsEnabled", p.hapticsEnabled)
        put("gyroMode", p.gyroMode.name)
        put("gyroSensitivity", p.gyroSensitivity.toDouble())
        put("portraitLayout", serializeLayoutMap(p.portraitLayout))
        put("landscapeLayout", serializeLayoutMap(p.landscapeLayout))
    }

    private fun serializeLayoutMap(map: Map<ControlElementId, ControlTransform>): JSONObject = JSONObject().apply {
        map.forEach { (id, t) ->
            put(id.name, JSONObject().apply {
                put("x", t.offsetXDp.toDouble())
                put("y", t.offsetYDp.toDouble())
                put("scale", t.scale.toDouble())
                put("opacity", t.opacity.toDouble())
            })
        }
    }

    private fun parseLayoutMap(obj: JSONObject?): Map<ControlElementId, ControlTransform> {
        val result = ControllerProfile.defaultControlMap().toMutableMap()
        if (obj == null) return result
        ControlElementId.entries.forEach { id ->
            val item = obj.optJSONObject(id.name)
            if (item != null) {
                result[id] = ControlTransform(
                    offsetXDp = item.optDouble("x", 0.0).toFloat(),
                    offsetYDp = item.optDouble("y", 0.0).toFloat(),
                    scale = item.optDouble("scale", 1.0).toFloat().coerceIn(0.4f, 2.0f),
                    opacity = item.optDouble("opacity", 0.85).toFloat().coerceIn(0.1f, 1.0f)
                )
            }
        }
        return result
    }

    private fun parseProfiles(raw: String): List<ControllerProfile> = try {
        val arr = JSONArray(raw)
        val list = mutableListOf<ControllerProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(ControllerProfile(
                id = o.optString("id", "profile_$i"),
                name = o.optString("name", "Profile ${i + 1}"),
                gameTitleMatch = o.optString("gameTitleMatch", ""),
                stickMode = runCatching { StickMode.valueOf(o.optString("stickMode", "FLOATING")) }.getOrDefault(StickMode.FLOATING),
                deadzone = o.optDouble("deadzone", 0.08).toFloat(),
                sensitivity = o.optDouble("sensitivity", 1.0).toFloat(),
                sensitivityCurve = runCatching { SensitivityCurve.valueOf(o.optString("sensitivityCurve", "LINEAR")) }.getOrDefault(SensitivityCurve.LINEAR),
                slideBetweenButtons = o.optBoolean("slideBetweenButtons", true),
                doubleTapStickClick = o.optBoolean("doubleTapStickClick", true),
                hapticsEnabled = o.optBoolean("hapticsEnabled", true),
                gyroMode = runCatching { GyroMode.valueOf(o.optString("gyroMode", "OFF")) }.getOrDefault(GyroMode.OFF),
                gyroSensitivity = o.optDouble("gyroSensitivity", 1.2).toFloat(),
                portraitLayout = parseLayoutMap(o.optJSONObject("portraitLayout")),
                landscapeLayout = parseLayoutMap(o.optJSONObject("landscapeLayout"))
            ))
        }
        list
    } catch (_: Exception) { defaultProfiles() }
}
