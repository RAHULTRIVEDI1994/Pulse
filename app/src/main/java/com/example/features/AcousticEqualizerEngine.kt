package com.example.features

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresApi
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
        bandOffsetsDb = listOf(1.5f, 0.5f, 0.0f, 3.0f, 2.0f)
    ),
    BASS_BOOST_MODE(
        displayName = "Bass Boost Mode",
        description = "Punchy low-end extension for open earbuds",
        bandOffsetsDb = listOf(6.5f, 4.0f, 0.0f, 1.0f, 2.0f),
        recommendedBassBoost = 750
    ),
    DEEP_SUB_BASS(
        displayName = "Deep Sub-Bass",
        description = "Maximum 60Hz-230Hz low-frequency drive",
        bandOffsetsDb = listOf(9.5f, 6.0f, 0.0f, -1.0f, 1.0f),
        recommendedBassBoost = 950
    ),
    VOCAL_PODCAST(
        displayName = "Podcast & Vocal",
        description = "Crisp midrange speech intelligibility",
        bandOffsetsDb = listOf(-3.0f, 0.5f, 4.5f, 4.0f, 1.0f)
    ),
    TREBLE_SPARK(
        displayName = "Treble Spark",
        description = "Airy high-frequency shimmer & acoustic detail",
        bandOffsetsDb = listOf(0f, 0f, 1.5f, 4.5f, 6.5f)
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
    val loudnessGainMb: Int = 600, // 0..1500 millibels (0..+15 dB)
    val selectedPreset: EqPreset = EqPreset.AIRPODS_CLARITY,
    val bands: List<EqBandUi> = defaultBands(),
    val hardwareAttached: Boolean = false,
    val statusNote: String = "Initializing audio DSP..."
) {
    companion object {
        fun defaultBands(): List<EqBandUi> = listOf(
            EqBandUi(0, "60 Hz", 1.5f, -15f, 15f),
            EqBandUi(1, "230 Hz", 0.5f, -15f, 15f),
            EqBandUi(2, "910 Hz", 0.0f, -15f, 15f),
            EqBandUi(3, "3.6 kHz", 3.0f, -15f, 15f),
            EqBandUi(4, "14 kHz", 2.0f, -15f, 15f)
        )
    }
}

class AcousticEqualizerEngine(private val context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var usingDynamicsProcessing = false

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
        val current = _state.value
        val anyActive = current.eqEnabled || current.bassBoostEnabled || current.loudnessEnabled

        if (!anyActive) {
            _state.update {
                it.copy(
                    hardwareAttached = true,
                    statusNote = "Audio DSP bypassed (0 dB attenuation)"
                )
            }
            return
        }

        // Prefer DynamicsProcessing on Android 9+ (API 28+): provides true inputGain Volume Boost
        // and PreEQ Bass Boost without legacy BassBoost's -6..-12 dB anti-clipping volume drop.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dpOk = runCatching { attachDynamicsProcessing(current) }.getOrDefault(false)
            if (dpOk) {
                usingDynamicsProcessing = true
                _state.update {
                    it.copy(
                        hardwareAttached = true,
                        statusNote = "DynamicsProcessing DSP active on system mix"
                    )
                }
                return
            }
        }

        // Fallback for API < 28 or OEM HALs that restrict DynamicsProcessing on session 0.
        usingDynamicsProcessing = false
        var eqOk = false
        var note = "Audio DSP active on system mix"

        runCatching {
            val eq = Equalizer(0, 0)
            eq.enabled = current.eqEnabled || current.bassBoostEnabled
            equalizer = eq
            eqOk = true

            val range = eq.bandLevelRange
            val minDb = (range[0] / 100f).coerceAtMost(-10f)
            val maxDb = (range[1] / 100f).coerceAtLeast(10f)
            val count = eq.numberOfBands.toInt().coerceAtLeast(1)
            val currentBands = current.bands

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
                EqBandUi(idx, label, clamped, minDb, maxDb)
            }
            _state.update { it.copy(bands = hwBands) }
            applyLegacyEqualizerBands(eq, hwBands, current)
        }.onFailure {
            note = "Global DSP restricted by device HAL; preset curve stored in app"
        }

        // Only instantiate LoudnessEnhancer when Volume Boost or Bass compensation is actually active
        // so disabled effects never trigger audio HAL pre-attenuation.
        if (current.loudnessEnabled || (current.bassBoostEnabled && !eqOk)) {
            runCatching {
                val le = LoudnessEnhancer(0)
                val targetMb = computeLegacyLoudnessMb(current)
                le.setTargetGain(targetMb)
                le.enabled = targetMb > 0
                loudnessEnhancer = le
            }
        }

        // Only fall back to legacy BassBoost if Equalizer failed to attach, because running both
        // Equalizer(0,0) and BassBoost(0,0) on libbundlewrapper triggers severe volume ducking.
        if (current.bassBoostEnabled && !eqOk) {
            runCatching {
                val bb = BassBoost(0, 0)
                bb.enabled = true
                if (bb.strengthSupported) {
                    bb.setStrength(current.bassBoostStrength.coerceIn(0, 1000).toShort())
                }
                bassBoost = bb
            }
        }

        _state.update {
            it.copy(
                hardwareAttached = eqOk || loudnessEnhancer != null || bassBoost != null,
                statusNote = note
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun attachDynamicsProcessing(current: EqualizerState): Boolean {
        val bandCount = current.bands.size.coerceAtLeast(5)
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true,
            bandCount,
            false,
            0,
            false,
            0,
            true
        )

        val inputGainDb = computeInputGainDb(current)
        builder.setInputGainAllChannelsTo(inputGainDb)

        val cutoffHz = floatArrayOf(120f, 450f, 1800f, 6500f, 18000f)
        for (ch in 0 until 2) {
            val preEq = DynamicsProcessing.Eq(true, true, bandCount)
            for (idx in 0 until bandCount) {
                val bandGain = computeEffectiveBandGainDb(idx, current)
                val freq = cutoffHz.getOrElse(idx) { 18000f }
                preEq.setBand(idx, DynamicsProcessing.EqBand(true, freq, bandGain))
            }
            builder.setPreEqByChannelIndex(ch, preEq)

            val needsLimiter = inputGainDb > 0.5f || current.bassBoostEnabled ||
                current.bands.any { it.levelDb > 3f }
            val limiter = DynamicsProcessing.Limiter(
                true,
                needsLimiter,
                0,
                1.5f,
                50.0f,
                3.5f,
                -0.8f,
                0.0f
            )
            builder.setLimiterByChannelIndex(ch, limiter)
        }

        val dp = DynamicsProcessing(0, 0, builder.build())
        dp.enabled = true
        dynamicsProcessing = dp
        return true
    }

    private fun syncEffects() {
        val current = _state.value
        val anyActive = current.eqEnabled || current.bassBoostEnabled || current.loudnessEnabled

        if (!anyActive) {
            attachAudioEffects()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && usingDynamicsProcessing) {
            val dp = dynamicsProcessing
            if (dp != null) {
                val updated = runCatching {
                    val inputGainDb = computeInputGainDb(current)
                    dp.setInputGainAllChannelsTo(inputGainDb)
                    val bandCount = current.bands.size.coerceAtLeast(5)
                    val cutoffHz = floatArrayOf(120f, 450f, 1800f, 6500f, 18000f)
                    for (ch in 0 until 2) {
                        val preEq = DynamicsProcessing.Eq(true, true, bandCount)
                        for (idx in 0 until bandCount) {
                            val freq = cutoffHz.getOrElse(idx) { 18000f }
                            preEq.setBand(
                                idx,
                                DynamicsProcessing.EqBand(true, freq, computeEffectiveBandGainDb(idx, current))
                            )
                        }
                        dp.setPreEqByChannelIndex(ch, preEq)
                    }
                    dp.enabled = true
                }.isSuccess
                if (updated) return
            }
        }

        // Fallback or re-attach cleanly
        attachAudioEffects()
    }

    private fun computeInputGainDb(current: EqualizerState): Float {
        var gain = 0f
        if (current.loudnessEnabled) {
            gain += (current.loudnessGainMb.coerceIn(0, MAX_LOUDNESS_MB) / 100f)
        }
        if (current.bassBoostEnabled) {
            // Subtle positive headroom lift so turning Bass Boost ON never drops perceived volume
            gain += 1.5f + (current.bassBoostStrength.coerceIn(0, 1000) / 1000f) * 1.5f
        }
        return gain.coerceIn(0f, 16f)
    }

    private fun computeEffectiveBandGainDb(bandIndex: Int, current: EqualizerState): Float {
        val eqDb = if (current.eqEnabled) {
            current.bands.getOrNull(bandIndex)?.levelDb ?: 0f
        } else {
            0f
        }
        val bassDb = if (current.bassBoostEnabled) {
            val ratio = current.bassBoostStrength.coerceIn(0, 1000) / 1000f
            when (bandIndex) {
                0 -> ratio * 8.0f
                1 -> ratio * 4.5f
                else -> 0f
            }
        } else {
            0f
        }
        return (eqDb + bassDb).coerceIn(-15f, 15f)
    }

    private fun applyLegacyEqualizerBands(
        eq: Equalizer,
        bands: List<EqBandUi>,
        current: EqualizerState
    ) {
        bands.forEachIndexed { idx, band ->
            val gainDb = computeEffectiveBandGainDb(idx, current).coerceIn(band.minDb, band.maxDb)
            runCatching {
                eq.setBandLevel(idx.toShort(), (gainDb * 100).toInt().toShort())
            }
        }
    }

    private fun computeLegacyLoudnessMb(current: EqualizerState): Int {
        var mb = 0
        if (current.loudnessEnabled) {
            mb += current.loudnessGainMb.coerceIn(0, MAX_LOUDNESS_MB)
        }
        if (current.bassBoostEnabled) {
            mb += 200
        }
        return mb.coerceIn(0, MAX_LOUDNESS_MB)
    }

    fun setEqEnabled(enabled: Boolean) {
        _state.update { it.copy(eqEnabled = enabled) }
        syncEffects()
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        _state.update { it.copy(bassBoostEnabled = enabled) }
        syncEffects()
    }

    fun toggleBassBoost(): Boolean {
        val next = !_state.value.bassBoostEnabled
        setBassBoostEnabled(next)
        return next
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.update { it.copy(bassBoostStrength = clamped) }
        syncEffects()
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        _state.update { it.copy(loudnessEnabled = enabled) }
        syncEffects()
    }

    fun setLoudnessGain(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, MAX_LOUDNESS_MB)
        _state.update { it.copy(loudnessGainMb = clamped) }
        syncEffects()
    }

    fun applyPreset(preset: EqPreset) {
        val currentBands = _state.value.bands
        val updatedBands = currentBands.mapIndexed { idx, band ->
            val targetDb = preset.bandOffsetsDb.getOrElse(idx) { 0f }
                .coerceIn(band.minDb, band.maxDb)
            band.copy(levelDb = targetDb)
        }

        val nextBassEnabled = if (preset == EqPreset.BASS_BOOST_MODE || preset == EqPreset.DEEP_SUB_BASS) {
            true
        } else {
            _state.value.bassBoostEnabled
        }
        val nextBassStrength = preset.recommendedBassBoost ?: _state.value.bassBoostStrength

        _state.update {
            it.copy(
                selectedPreset = preset,
                bands = updatedBands,
                bassBoostEnabled = nextBassEnabled,
                bassBoostStrength = nextBassStrength
            )
        }
        syncEffects()
    }

    fun updateBandLevel(bandIndex: Int, levelDb: Float) {
        val updatedBands = _state.value.bands.map { band ->
            if (band.index == bandIndex) {
                band.copy(levelDb = levelDb.coerceIn(band.minDb, band.maxDb))
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
        syncEffects()
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
        runCatching { dynamicsProcessing?.release() }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudnessEnhancer?.release() }
        dynamicsProcessing = null
        usingDynamicsProcessing = false
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

    companion object {
        const val MAX_LOUDNESS_MB = 1500 // +15.0 dB
    }
}
