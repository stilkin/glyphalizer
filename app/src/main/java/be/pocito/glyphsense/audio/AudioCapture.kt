package be.pocito.glyphsense.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Captures microphone audio via [AudioRecord] at 44.1 kHz, mono, 16-bit PCM.
 *
 * Emits fixed-size buffers of samples through [buffers]. Buffer size defaults to
 * 2048 samples (~46ms at 44.1 kHz) — good tradeoff between FFT resolution and latency.
 *
 * Caller is responsible for ensuring RECORD_AUDIO permission has been granted
 * before calling [start].
 */
class AudioCapture(
    private val bufferSamples: Int = DEFAULT_BUFFER_SAMPLES,
) {
    companion object {
        const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 44_100
        const val DEFAULT_BUFFER_SAMPLES = 2048
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _buffers = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Stream of captured audio buffers, each of size [bufferSamples]. */
    val buffers: SharedFlow<ShortArray> = _buffers

    private var audioRecord: AudioRecord? = null
    private var scope: CoroutineScope? = null
    private var captureJob: Job? = null

    @Volatile
    private var running: Boolean = false

    fun isRunning(): Boolean = running

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
        )
        if (minBufferBytes <= 0) {
            Log.e(TAG, "getMinBufferSize returned $minBufferBytes; AudioRecord not available")
            return
        }
        // AudioRecord internal buffer: at least 4x our read chunk, rounded up to min required
        val readChunkBytes = bufferSamples * 2 // 16-bit = 2 bytes/sample
        val internalBufferBytes = maxOf(minBufferBytes, readChunkBytes * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            internalBufferBytes,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize (state=${recorder.state})")
            recorder.release()
            return
        }
        audioRecord = recorder
        recorder.startRecording()
        running = true

        val captureScope = CoroutineScope(Dispatchers.IO)
        scope = captureScope
        captureJob = captureScope.launch {
            val buf = ShortArray(bufferSamples)
            while (running) {
                val read = recorder.read(buf, 0, bufferSamples)
                if (read > 0) {
                    // Copy so downstream doesn't see the same array being overwritten
                    val out = buf.copyOf(read)
                    _buffers.tryEmit(out)
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord.read returned error code $read")
                    break
                }
            }
        }
        Log.d(TAG, "Started (minBuffer=$minBufferBytes bytes, internal=$internalBufferBytes bytes)")
    }

    fun stop() {
        running = false
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stop() while not recording: ${e.message}")
        }
        audioRecord?.release()
        audioRecord = null
        captureJob?.cancel()
        captureJob = null
        scope?.cancel()
        scope = null
        Log.d(TAG, "Stopped")
    }
}

/** Returns the peak absolute sample value in a buffer, 0..32767. */
fun ShortArray.peakAmplitude(): Int {
    var peak = 0
    for (s in this) {
        val a = abs(s.toInt())
        if (a > peak) peak = a
    }
    return peak
}
