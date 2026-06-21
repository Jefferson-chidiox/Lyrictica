package com.lyrictica.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * Simple biquad IIR filter (Direct Form I).
 *
 * Coefficients follow RBJ Audio EQ Cookbook conventions.
 */
internal class BiquadFilter private constructor(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float
) {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }

    companion object {
        fun lowPass(sampleRateHz: Int, cutoffHz: Float, q: Float = 0.707f): BiquadFilter {
            val w0 = (2.0 * Math.PI * cutoffHz / sampleRateHz.toDouble()).toFloat()
            val cosw = cos(w0)
            val sinw = sin(w0)
            val alpha = (sinw / (2f * q))

            val b0 = (1f - cosw) / 2f
            val b1 = 1f - cosw
            val b2 = (1f - cosw) / 2f
            val a0 = 1f + alpha
            val a1 = -2f * cosw
            val a2 = 1f - alpha

            return normalized(b0, b1, b2, a0, a1, a2)
        }

        fun highPass(sampleRateHz: Int, cutoffHz: Float, q: Float = 0.707f): BiquadFilter {
            val w0 = (2.0 * Math.PI * cutoffHz / sampleRateHz.toDouble()).toFloat()
            val cosw = cos(w0)
            val sinw = sin(w0)
            val alpha = (sinw / (2f * q))

            val b0 = (1f + cosw) / 2f
            val b1 = -(1f + cosw)
            val b2 = (1f + cosw) / 2f
            val a0 = 1f + alpha
            val a1 = -2f * cosw
            val a2 = 1f - alpha

            return normalized(b0, b1, b2, a0, a1, a2)
        }

        private fun normalized(
            b0: Float,
            b1: Float,
            b2: Float,
            a0: Float,
            a1: Float,
            a2: Float
        ): BiquadFilter {
            val invA0 = 1f / a0
            return BiquadFilter(
                b0 = b0 * invA0,
                b1 = b1 * invA0,
                b2 = b2 * invA0,
                a1 = a1 * invA0,
                a2 = a2 * invA0
            )
        }
    }
}
