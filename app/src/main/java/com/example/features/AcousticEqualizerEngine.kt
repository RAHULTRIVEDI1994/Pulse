package com.example.features

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

enum class EqPreset(
    val displayName: String,
    val description: String,
    val bandOffsetsDb: List<Float>, // 5 normalized bands (-15f..15f dB)
    val recommendedBassBoost: Int? = null
) {
    FLAT(
        displayName = "Flat Studio",
        description = "Uncolored reference response",
        bandOffsetsDb = listOf(0f, 0f, 0f, 0f, 0f)
    ),
    AIRPODS_CLARITY(
        displayName = "AirPods 1 Clarity",
        description = "Balanced presence & open-fit warmth",
        bandOffsetsDb = listOf(2.5f, 1.5f, 1.5f, 4.0f, 3.0f)
    ),
    BASS_BOOST_MODE(
        displayName = "Bass Boost Mode",
        description = "Punchy low-end extension for open earbuds",
        bandOffsetsDb = listOf(9.0f, 6.0f, 1.0f, 1.5f, 2.5f),
        recommendedBassBoost = 750
    ),
    DEEP_SUB_BASS(
        displayName = "Deep Sub-Bass",
        description = "Maximum 60Hz-230Hz low-frequency drive",
        bandOffsetsDb = listOf(12.0f, 8.0f, 0f, -1.0f, 1.5f),
        recommendedBassBoost = 950
    ),
    VOCAL_PODCAST(
        displayName = "Podcast & Vocal",
        description = "Crisp midrange speech intelligibility",
        bandOffsetsDb = listOf(-3.0f, 1.0f, 5.5f, 4.5f, 1.5f)
    ),
    TREBLE_SPARK(
        displayName = "Treble Spark",
        description = "Airy high-frequency shimmer & acoustic detail",
        bandOffsetsDb = listOf(0f, 0f, 2.0f, 5.0f, 7.5f)
    ),
    CUSTOM(
        displayName = "Custom",
        description = "User-tuned multi-band curve",
        bandOffsetsDb = listOf(0f, 0f, 0f, 0f, 0f)
    )
}

data class EqBandUi(
    val index: Int,
    val label: String,
    val levelDb: Float,
    val minDb: Float,
    val maxDb: Float
)

data class EqualizerState(
    val eqEnabled: Boolean = true,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 650, // 0..1000
    val loudnessEnabled: Boolean = false,
    val loudnessGainMb: Int = 350, // 0..1000 millibels
    val selectedPreset: EqPreset = EqPreset.AIRPODS_CLARITY,
    val bands: List<EqBandUi> = defaultBands(),
    val hardwareAttached: Boolean = false,
    val statusNote: String = "Initializing audio DSP..."
) {
    companion object {
        fun defaultBands(): List<EqBandUi> = listOf(
            EqBandUi(0, "60 Hz", 2.5f, -15f, 15f),
            EqBandUi(1, "230 Hz", 1.5f, -15f, 15f),
            EqBandUi(2, "910 Hz", 1.5f, -15f, 15f),
            EqBandUi(3, "3.6 kHz", 4.0f, -15f, 15f),
            EqBandUi(4, "14 kHz", 3.0f, -15f, 15f)
        )
    }
}

class AcousticEqualizerEngine(private val context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    init {
        attachAudioEffects()
    }

    fun attachAudioEffects() {
        releaseEffects()
        var eqOk = false
        var note = "Audio DSP active on system mix"

        runCatching {
            val eq = Equalizer(0, 0)
            eq.enabled = _state.value.eqEnabled
            equalizer = eq
            eqOk = true

            val range = eq.bandLevelRange
            val minDb = (range[0] / 100f).coerceAtMost(-10f)
            val maxDb = (range[1] / 100f).coerceAtLeast(10f)
            val count = eq.numberOfBands.toInt().coerceAtLeast(1)
            val currentBands = _state.value.bands

            val hwBands = (0 until count).map { idx ->
                val centerHz = eq.getCenterFreq(idx.toShort()) / 1000
                val label = if (centerHz >= 1000) {
                    val khz = centerHz / 1000f
                    if (khz % 1f == 0f) "${khz.toInt()} kHz" else "${"%.1f".format(khz)} kHz"
                } else {
                    "$centerHz Hz"
                }
                val existingDb = currentBands.getOrNull(idx)?.levelDb ?: 0f
                val clamped = existingDb.coerceIn(minDb, maxDb)
                eq.setBandLevel(idx.toShort(), (clamped * 100).toInt().toShort())
                EqBandUi(idx, label, clamped, minDb, maxDb)
            }
            _state.update { it.copy(bands = hwBands) }
        }.onFailure {
            note = "Global DSP restricted by device HAL; preset curve stored in app"
        }

        runCatching {
            val bb = BassBoost(0, 0)
            bb.enabled = _state.value.bassBoostEnabled
            if (bb.strengthSupported) {
                bb.setStrength(_state.value.bassBoostStrength.coerceIn(0, 1000).toShort())
            }
            bassBoost = bb
        }

        runCatching {
            val le = LoudnessEnhancer(0)
            le.setTargetGain(_state.value.loudnessGainMb.coerceIn(0, 1000))
            le.enabled = _state.value.loudnessEnabled
            loudnessEnhancer = le
        }

        _state.update {
            it.copy(
                hardwareAttached = eqOk,
                statusNote = note
            )
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        _state.update { it.copy(eqEnabled = enabled) }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        runCatching {
            bassBoost?.enabled = enabled
            if (enabled && bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(_state.value.bassBoostStrength.coerceIn(0, 1000).toShort())
            }
        }
        _state.update { it.copy(bassBoostEnabled = enabled) }
    }

    fun toggleBassBoost(): Boolean {
        val next = !_state.value.bassBoostEnabled
        setBassBoostEnabled(next)
        return next
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(clamped.toShort())
            }
        }
        _state.update { it.copy(bassBoostStrength = clamped) }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        runCatching {
            loudnessEnhancer?.setTargetGain(_state.value.loudnessGainMb)
            loudnessEnhancer?.enabled = enabled
        }
        _state.update { it.copy(loudnessEnabled = enabled) }
    }

    fun setLoudnessGain(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1000)
        runCatching { loudnessEnhancer?.setTargetGain(clamped) }
        _state.update { it.copy(loudnessGainMb = clamped) }
    }

    fun applyPreset(preset: EqPreset) {
        val currentBands = _state.value.bands
        val updatedBands = currentBands.mapIndexed { idx, band ->
            val targetDb = preset.bandOffsetsDb.getOrElse(idx) { 0f }
                .coerceIn(band.minDb, band.maxDb)
            runCatching {
                equalizer?.setBandLevel(idx.toShort(), (targetDb * 100).toInt().toShort())
            }
            band.copy(levelDb = targetDb)
        }

        val nextBassEnabled = if (preset == EqPreset.BASS_BOOST_MODE || preset == EqPreset.DEEP_SUB_BASS) {
            true
        } else {
            _state.value.bassBoostEnabled
        }
        val nextBassStrength = preset.recommendedBassBoost ?: _state.value.bassBoostStrength

        runCatching {
            bassBoost?.enabled = nextBassEnabled
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(nextBassStrength.toShort())
            }
        }

        _state.update {
            it.copy(
                selectedPreset = preset,
                bands = updatedBands,
                bassBoostEnabled = nextBassEnabled,
                bassBoostStrength = nextBassStrength
            )
        }
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Float) {
        val updatedBands = _state.value.bands.map { band ->
            if (band.index == bandIndex) {
                val clamped = levelDb.coerceIn(band.minDb, band.maxDb)
                runCatching {
                    equalizer?.setBandLevel(bandIndex.toShort(), (clamped * 100).toInt().toShort())
                }
                band.copy(levelDb = clamped)
            } else {
                band
            }
        }
        _state.update {
            it.copy(
                selectedPreset = EqPreset.CUSTOM,
                bands = updatedBands
            )
        }
    }

    fun getCurrentMusicVolume(): Pair<Int, Int> {
        val am = audioManager ?: return 0 to 15
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return current to max
    }

    fun setMusicVolume(volume: Int) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceIn(0, max), 0)
    }

    fun speakText(message: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "podpulse_tts")
            return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "podpulse_tts")
            }
        }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
    }

    fun shutdown() {
        releaseEffects()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
    }
}
