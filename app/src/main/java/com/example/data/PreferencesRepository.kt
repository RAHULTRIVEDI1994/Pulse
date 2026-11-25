package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.features.EqPreset
import com.example.gesture.AirPodsInputGesture
import com.example.gesture.GestureMappingConfig
import com.example.gesture.SystemIntentAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.podPulseDataStore: DataStore<Preferences> by preferencesDataStore(name = "podpulse_prefs")

data class CompanionSettings(
    val popupOnLidOpen: Boolean = true,
    val autoEarDetectionPause: Boolean = true,
    val autoEarDetectionResume: Boolean = true,
    val announceBatteryOnConnect: Boolean = false,
    val monitorServiceEnabled: Boolean = false,
    val lowBatteryAlertPercent: Int = 20,
    val eqEnabled: Boolean = true,
    val eqPreset: EqPreset = EqPreset.AIRPODS_CLARITY,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 650,
    val loudnessEnabled: Boolean = false,
    val customBandLevels: List<Float> = EqPreset.AIRPODS_CLARITY.bandOffsetsDb
)

class PreferencesRepository(private val context: Context) {

    private val dataStore = context.podPulseDataStore

    val gestureConfigFlow: Flow<GestureMappingConfig> = dataStore.data.map { prefs ->
        val defaultMap = GestureMappingConfig.defaultMappings()
        val resolvedMap = AirPodsInputGesture.entries.associateWith { gesture ->
            val raw = prefs[gestureKey(gesture)]
            SystemIntentAction.entries.firstOrNull { it.name == raw } ?: (defaultMap[gesture] ?: SystemIntentAction.NO_ACTION)
        }
        GestureMappingConfig(
            enabled = prefs[KEY_GESTURES_ENABLED] ?: true,
            interceptMediaButtons = prefs[KEY_INTERCEPT_MEDIA_BUTTONS] ?: true,
            requireAirPodsConnected = prefs[KEY_REQUIRE_AIRPODS_CONNECTED] ?: false,
            multiPressWindowMs = prefs[KEY_MULTI_PRESS_WINDOW_MS] ?: 450,
            volumeStepCount = prefs[KEY_VOLUME_STEPS] ?: 1,
            mappings = resolvedMap
        )
    }

    val companionSettingsFlow: Flow<CompanionSettings> = dataStore.data.map { prefs ->
        val presetName = prefs[KEY_EQ_PRESET]
        val preset = EqPreset.entries.firstOrNull { it.name == presetName } ?: EqPreset.AIRPODS_CLARITY
        val bandLevels = (0 until 5).map { idx ->
            prefs[bandKey(idx)] ?: preset.bandOffsetsDb.getOrElse(idx) { 0f }
        }
        CompanionSettings(
            popupOnLidOpen = prefs[KEY_POPUP_ON_LID] ?: true,
            autoEarDetectionPause = prefs[KEY_AUTO_EAR_PAUSE] ?: true,
            autoEarDetectionResume = prefs[KEY_AUTO_EAR_RESUME] ?: true,
            announceBatteryOnConnect = prefs[KEY_ANNOUNCE_BATTERY] ?: false,
            monitorServiceEnabled = prefs[KEY_MONITOR_SERVICE] ?: false,
            lowBatteryAlertPercent = prefs[KEY_LOW_BATTERY_THRESHOLD] ?: 20,
            eqEnabled = prefs[KEY_EQ_ENABLED] ?: true,
            eqPreset = preset,
            bassBoostEnabled = prefs[KEY_BASS_BOOST_ENABLED] ?: false,
            bassBoostStrength = prefs[KEY_BASS_BOOST_STRENGTH] ?: 650,
            loudnessEnabled = prefs[KEY_LOUDNESS_ENABLED] ?: false,
            customBandLevels = bandLevels
        )
    }

    suspend fun updateGestureMapping(gesture: AirPodsInputGesture, action: SystemIntentAction) {
        dataStore.edit { prefs ->
            prefs[gestureKey(gesture)] = action.name
        }
    }

    suspend fun updateGestureEngineOptions(
        enabled: Boolean? = null,
        interceptMediaButtons: Boolean? = null,
        requireAirPodsConnected: Boolean? = null,
        multiPressWindowMs: Int? = null,
        volumeStepCount: Int? = null
    ) {
        dataStore.edit { prefs ->
            enabled?.let { prefs[KEY_GESTURES_ENABLED] = it }
            interceptMediaButtons?.let { prefs[KEY_INTERCEPT_MEDIA_BUTTONS] = it }
            requireAirPodsConnected?.let { prefs[KEY_REQUIRE_AIRPODS_CONNECTED] = it }
            multiPressWindowMs?.let { prefs[KEY_MULTI_PRESS_WINDOW_MS] = it }
            volumeStepCount?.let { prefs[KEY_VOLUME_STEPS] = it }
        }
    }

    suspend fun resetGestureMappings() {
        dataStore.edit { prefs ->
            GestureMappingConfig.defaultMappings().forEach { (gesture, action) ->
                prefs[gestureKey(gesture)] = action.name
            }
            prefs[KEY_GESTURES_ENABLED] = true
            prefs[KEY_INTERCEPT_MEDIA_BUTTONS] = true
            prefs[KEY_REQUIRE_AIRPODS_CONNECTED] = false
            prefs[KEY_MULTI_PRESS_WINDOW_MS] = 450
            prefs[KEY_VOLUME_STEPS] = 1
        }
    }

    suspend fun updateCompanionSettings(transform: (CompanionSettings) -> CompanionSettings) {
        dataStore.edit { prefs ->
            val current = CompanionSettings(
                popupOnLidOpen = prefs[KEY_POPUP_ON_LID] ?: true,
                autoEarDetectionPause = prefs[KEY_AUTO_EAR_PAUSE] ?: true,
                autoEarDetectionResume = prefs[KEY_AUTO_EAR_RESUME] ?: true,
                announceBatteryOnConnect = prefs[KEY_ANNOUNCE_BATTERY] ?: false,
                monitorServiceEnabled = prefs[KEY_MONITOR_SERVICE] ?: false,
                lowBatteryAlertPercent = prefs[KEY_LOW_BATTERY_THRESHOLD] ?: 20,
                eqEnabled = prefs[KEY_EQ_ENABLED] ?: true,
                eqPreset = EqPreset.entries.firstOrNull { it.name == prefs[KEY_EQ_PRESET] }
                    ?: EqPreset.AIRPODS_CLARITY,
                bassBoostEnabled = prefs[KEY_BASS_BOOST_ENABLED] ?: false,
                bassBoostStrength = prefs[KEY_BASS_BOOST_STRENGTH] ?: 650,
                loudnessEnabled = prefs[KEY_LOUDNESS_ENABLED] ?: false,
                customBandLevels = (0 until 5).map { idx -> prefs[bandKey(idx)] ?: 0f }
            )
            val updated = transform(current)
            prefs[KEY_POPUP_ON_LID] = updated.popupOnLidOpen
            prefs[KEY_AUTO_EAR_PAUSE] = updated.autoEarDetectionPause
            prefs[KEY_AUTO_EAR_RESUME] = updated.autoEarDetectionResume
            prefs[KEY_ANNOUNCE_BATTERY] = updated.announceBatteryOnConnect
            prefs[KEY_MONITOR_SERVICE] = updated.monitorServiceEnabled
            prefs[KEY_LOW_BATTERY_THRESHOLD] = updated.lowBatteryAlertPercent
            prefs[KEY_EQ_ENABLED] = updated.eqEnabled
            prefs[KEY_EQ_PRESET] = updated.eqPreset.name
            prefs[KEY_BASS_BOOST_ENABLED] = updated.bassBoostEnabled
            prefs[KEY_BASS_BOOST_STRENGTH] = updated.bassBoostStrength
            prefs[KEY_LOUDNESS_ENABLED] = updated.loudnessEnabled
            updated.customBandLevels.forEachIndexed { idx, value ->
                prefs[bandKey(idx)] = value
            }
        }
    }

    private companion object {
        val KEY_GESTURES_ENABLED = booleanPreferencesKey("gestures_enabled")
        val KEY_INTERCEPT_MEDIA_BUTTONS = booleanPreferencesKey("intercept_media_buttons")
        val KEY_REQUIRE_AIRPODS_CONNECTED = booleanPreferencesKey("require_airpods_connected")
        val KEY_MULTI_PRESS_WINDOW_MS = intPreferencesKey("multi_press_window_ms")
        val KEY_VOLUME_STEPS = intPreferencesKey("volume_steps")

        val KEY_POPUP_ON_LID = booleanPreferencesKey("popup_on_lid_open")
        val KEY_AUTO_EAR_PAUSE = booleanPreferencesKey("auto_ear_pause")
        val KEY_AUTO_EAR_RESUME = booleanPreferencesKey("auto_ear_resume")
        val KEY_ANNOUNCE_BATTERY = booleanPreferencesKey("announce_battery")
        val KEY_MONITOR_SERVICE = booleanPreferencesKey("monitor_service_enabled")
        val KEY_LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")

        val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        val KEY_BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val KEY_BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val KEY_LOUDNESS_ENABLED = booleanPreferencesKey("loudness_enabled")

        fun gestureKey(gesture: AirPodsInputGesture) =
            stringPreferencesKey("gesture_map_${gesture.name.lowercase()}")

        fun bandKey(index: Int) = floatPreferencesKey("eq_band_$index")
    }
}
