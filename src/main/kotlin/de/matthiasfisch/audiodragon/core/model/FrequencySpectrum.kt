package de.matthiasfisch.audiodragon.core.model

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Produces a one-sided, full-scale amplitude spectrum for real PCM samples.
 *
 * A periodic Hann window limits spectral leakage, while coherent-gain correction keeps a
 * bin-centred full-scale sine wave at approximately 1.0 regardless of buffer size. The
 * Nyquist bin is intentionally omitted to retain the previous `N / 2` result shape.
 */
object FrequencySpectrum {
    fun magnitudes(samples: FloatArray): DoubleArray {
        require(samples.isNotEmpty()) { "At least one PCM sample is required." }

        val coefficients = FloatArray(samples.size)
        val windowSum = if (samples.size == 1) {
            coefficients[0] = samples[0]
            1.0
        } else {
            samples.indices.sumOf { index ->
                val window = 0.5 * (1.0 - cos(2.0 * PI * index / samples.size))
                coefficients[index] = (samples[index] * window).toFloat()
                window
            }
        }

        FloatFFT_1D(samples.size.toLong()).realForward(coefficients)

        return DoubleArray(samples.size / 2) { bin ->
            val magnitude = if (bin == 0) {
                abs(coefficients[0].toDouble())
            } else {
                hypot(coefficients[2 * bin].toDouble(), coefficients[2 * bin + 1].toDouble())
            }
            magnitude * if (bin == 0) 1.0 / windowSum else 2.0 / windowSum
        }
    }
}
