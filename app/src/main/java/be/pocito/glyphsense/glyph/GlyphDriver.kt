package be.pocito.glyphsense.glyph

import be.pocito.glyphsense.audio.AudioAnalysis
import be.pocito.glyphsense.model.DeviceProfile
import be.pocito.glyphsense.model.VisualizerSettings
import kotlin.math.roundToInt

/**
 * Maps [AudioAnalysis] output onto the LED layout described by a [DeviceProfile].
 *
 * Each device has different LED counts and zone assignments. The driver adapts:
 * - Spectrum indices get one FFT band each (count matches profile)
 * - Bass indices fill bottom-up proportional to bass energy
 * - Beat indices flash on detected beats with decay
 * - If a zone is null (e.g. Phone 4a), that visualization is skipped
 */
class GlyphDriver(
    private val profile: DeviceProfile,
    private val beatDecayFrames: Int = 6,
) {
    companion object {
        const val MAX_BRIGHTNESS = 4095
    }

    private val out = IntArray(profile.ledCount)
    private var beatFrames: Int = 0

    fun render(analysis: AudioAnalysis, settings: VisualizerSettings = VisualizerSettings()): IntArray {
        for (i in out.indices) out[i] = 0

        if (settings.zoneCEnabled) renderSpectrum(analysis.spectrum, settings.brightness)
        if (settings.zoneAEnabled) renderBass(analysis.bassLevel, settings.brightness)
        if (analysis.beat) beatFrames = beatDecayFrames
        if (settings.zoneBEnabled) renderBeat(settings.brightness)
        if (beatFrames > 0) beatFrames--

        return out
    }

    fun blankFrame(): IntArray {
        for (i in out.indices) out[i] = 0
        return out
    }

    private fun renderSpectrum(spectrum: FloatArray, brightness: Float) {
        val indices = profile.spectrumIndices
        val n = minOf(spectrum.size, indices.size)
        for (i in 0 until n) {
            out[indices[i]] = scale(spectrum[i], brightness)
        }
    }

    private fun renderBass(bassLevel: Float, brightness: Float) {
        val indices = profile.bassIndices ?: return
        val lit = (bassLevel.coerceIn(0f, 1f) * indices.size).roundToInt()
        val maxB = scale(1f, brightness)
        // Fill from the last index (bottom) toward the first (top)
        for (k in 0 until lit) {
            val idx = indices.size - 1 - k
            if (idx >= 0) out[indices[idx]] = maxB
        }
    }

    private fun renderBeat(brightness: Float) {
        val indices = profile.beatIndices ?: return
        if (beatFrames <= 0) return
        val fraction = beatFrames.toFloat() / beatDecayFrames
        val level = scale(fraction, brightness)
        for (idx in indices) {
            out[idx] = level
        }
    }

    private fun scale(level: Float, brightness: Float): Int {
        val v = (level * brightness * MAX_BRIGHTNESS).roundToInt()
        return v.coerceIn(0, MAX_BRIGHTNESS)
    }
}
