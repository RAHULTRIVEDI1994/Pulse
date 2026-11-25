package com.example.features

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class FindMyChannel(val label: String) {
    LEFT("Left Pod"),
    RIGHT("Right Pod"),
    BOTH("Both Pods")
}

/**
 * Generates an acoustic locating chirp routed exclusively to the Left, Right, or Both stereo channels
 * to help locate misplaced AirPods (mirrors iOS Find My "Play Sound").
 */
class FindMyAudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null

    private val _activeChannel = MutableStateFlow<FindMyChannel?>(null)
    val activeChannel: StateFlow<FindMyChannel?> = _activeChannel.asStateFlow()

    fun startChirp(channel: FindMyChannel) {
        stop()
        _activeChannel.value = channel

        playJob = scope.launch {
            val sampleRate = 44100
            val cycleDurationSec = 1.2
            val totalFrames = (sampleRate * cycleDurationSec).toInt()
            val pcm = ShortArray(totalFrames * 2) // Stereo interleaved L, R

            val leftGain = if (channel == FindMyChannel.LEFT || channel == FindMyChannel.BOTH) 1.0 else 0.0
            val rightGain = if (channel == FindMyChannel.RIGHT || channel == FindMyChannel.BOTH) 1.0 else 0.0

            // Build 3 crisp rising acoustic pings followed by a brief pause
            for (frame in 0 until totalFrames) {
                val t = frame.toDouble() / sampleRate
                val inPingWindow = (t in 0.0..0.18) || (t in 0.24..0.42) || (t in 0.48..0.72)
                val sample = if (inPingWindow) {
                    val freq = when {
                        t < 0.24 -> 2200.0
                        t < 0.48 -> 2800.0
                        else -> 3400.0
                    }
                    val envelope = sin((t % 0.24) / 0.18 * PI).coerceIn(0.0, 1.0)
                    (sin(2.0 * PI * freq * t) * envelope * 26000.0)
                } else {
                    0.0
                }
                pcm[frame * 2] = (sample * leftGain).toInt().toShort()
                pcm[frame * 2 + 1] = (sample * rightGain).toInt().toShort()
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferBytes = maxOf(minBuf, pcm.size * 2)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            }.getOrNull()

            if (track == null) {
                _activeChannel.value = null
                return@launch
            }

            try {
                track.play()
                while (isActive) {
                    val written = track.write(pcm, 0, pcm.size)
                    if (written < 0) break
                }
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        _activeChannel.value = null
    }
}
