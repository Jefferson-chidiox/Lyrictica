package com.lyrictica.karaoke

import com.lyrictica.audio.PcmDecoder
import com.lyrictica.lyrics.ParsedLyrics
import java.io.File
import kotlin.math.sqrt
import kotlinx.serialization.json.Json

internal object KaraokeMelodyPrecomputer {
    private val json = Json { ignoreUnknownKeys = true }

    fun computeAndCache(
        audioFile: File,
        parsedLyrics: ParsedLyrics,
        cacheFile: File
    ): KaraokeMelodyReference {
        val reference = compute(audioFile, parsedLyrics)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(json.encodeToString(KaraokeMelodyReference.serializer(), reference))
        return reference
    }

    fun readCache(cacheFile: File): KaraokeMelodyReference? {
        if (!cacheFile.isFile || cacheFile.length() <= 0L) return null
        return runCatching {
            json.decodeFromString(KaraokeMelodyReference.serializer(), cacheFile.readText())
        }.getOrNull()
    }

    private fun compute(
        audioFile: File,
        parsedLyrics: ParsedLyrics
    ): KaraokeMelodyReference {
        val words = parsedLyrics.lines.flatMapIndexed { lineIndex, line ->
            line.words.mapIndexedNotNull { wordIndex, word ->
                val text = word.text.trim()
                if (text.isBlank()) null else IndexedWordWindow(
                    lineIndex = lineIndex,
                    wordIndex = wordIndex,
                    text = text,
                    startTimeMs = word.startTimeMs,
                    endTimeMs = word.endTimeMs.coerceAtLeast(word.startTimeMs + 1L)
                )
            }
        }
        if (words.isEmpty()) return KaraokeMelodyReference(emptyList())

        var sampleRate = 44_100
        val collector = FloatChunkAccumulator()
        PcmDecoder.decodeToMonoFloat(
            file = audioFile,
            onFormat = { fmt -> sampleRate = fmt.sampleRateHz },
            onPcmChunk = { chunk -> collector.append(chunk) }
        )

        val samples = collector.toFloatArray()
        if (samples.isEmpty()) return KaraokeMelodyReference(words.map { it.toReference(null, 0f, 0) })

        val pitchFrames = detectPitchFrames(samples, sampleRate)
        val aggregated = Array(words.size) { PitchAccumulator() }
        var wordPointer = 0

        for (frame in pitchFrames) {
            while (wordPointer < words.lastIndex && frame.centerTimeMs > words[wordPointer].endTimeMs) {
                wordPointer++
            }
            val word = words.getOrNull(wordPointer) ?: break
            if (frame.centerTimeMs < word.startTimeMs || frame.centerTimeMs > word.endTimeMs) continue
            val pitchHz = frame.pitchHz ?: continue
            aggregated[wordPointer].add(pitchHz, frame.confidence)
        }

        val references = words.mapIndexed { index, word ->
            val summary = aggregated[index].summary()
            word.toReference(summary?.pitchHz, summary?.confidence ?: 0f, summary?.sampleCount ?: 0)
        }

        return KaraokeMelodyReference(references)
    }

    private fun detectPitchFrames(samples: FloatArray, sampleRate: Int): List<PitchFrame> {
        val frameSize = 2048
        val hopSize = 512
        if (samples.size < frameSize || sampleRate <= 0) return emptyList()

        val frames = ArrayList<PitchFrame>((samples.size - frameSize) / hopSize + 1)
        var frameStart = 0
        while (frameStart + frameSize <= samples.size) {
            val (pitchHz, confidence) = detectPitch(samples, frameStart, frameSize, sampleRate)
            val centerTimeMs = ((frameStart + (frameSize / 2)) * 1000L / sampleRate.toLong())
            frames += PitchFrame(
                centerTimeMs = centerTimeMs,
                pitchHz = pitchHz,
                confidence = confidence
            )
            frameStart += hopSize
        }
        return frames
    }

    private fun detectPitch(samples: FloatArray, offset: Int, frameSize: Int, sampleRate: Int): Pair<Float?, Float> {
        val minLag = (sampleRate / 880f).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / 80f).toInt().coerceAtMost(frameSize - 1)
        val mean = samples.copyOfRange(offset, offset + frameSize).average().toFloat()
        val frame = FloatArray(frameSize) { index -> samples[offset + index] - mean }

        var energy = 0f
        for (value in frame) {
            energy += value * value
        }
        if (energy <= 1e-6f) return null to 0f

        var bestLag = -1
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            var correlation = 0f
            var lhsEnergy = 0f
            var rhsEnergy = 0f
            val limit = frameSize - lag
            for (index in 0 until limit) {
                val lhs = frame[index]
                val rhs = frame[index + lag]
                correlation += lhs * rhs
                lhsEnergy += lhs * lhs
                rhsEnergy += rhs * rhs
            }
            if (lhsEnergy <= 1e-6f || rhsEnergy <= 1e-6f) continue
            val score = correlation / sqrt(lhsEnergy * rhsEnergy)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        if (bestLag <= 0 || bestScore < 0.42f) return null to bestScore
        return (sampleRate.toFloat() / bestLag.toFloat()) to bestScore
    }

    private data class IndexedWordWindow(
        val lineIndex: Int,
        val wordIndex: Int,
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long
    ) {
        fun toReference(expectedPitchHz: Float?, confidence: Float, sampleCount: Int): KaraokeMelodyWordReference {
            return KaraokeMelodyWordReference(
                lineIndex = lineIndex,
                wordIndex = wordIndex,
                text = text,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                expectedPitchHz = expectedPitchHz,
                averageConfidence = confidence,
                sampleCount = sampleCount
            )
        }
    }

    private data class PitchFrame(
        val centerTimeMs: Long,
        val pitchHz: Float?,
        val confidence: Float
    )

    private data class PitchSummary(
        val pitchHz: Float,
        val confidence: Float,
        val sampleCount: Int
    )

    private class PitchAccumulator {
        private val pitches = ArrayList<Float>()
        private var confidenceSum = 0f

        fun add(pitchHz: Float, confidence: Float) {
            pitches += pitchHz
            confidenceSum += confidence
        }

        fun summary(): PitchSummary? {
            if (pitches.size < 2) return null
            val sorted = pitches.sorted()
            val median = sorted[sorted.size / 2]
            return PitchSummary(
                pitchHz = median,
                confidence = confidenceSum / pitches.size.toFloat(),
                sampleCount = pitches.size
            )
        }
    }

    private class FloatChunkAccumulator {
        private val chunks = ArrayList<FloatArray>()
        private var totalSize = 0

        fun append(chunk: FloatArray) {
            chunks += chunk.copyOf()
            totalSize += chunk.size
        }

        fun toFloatArray(): FloatArray {
            val output = FloatArray(totalSize)
            var position = 0
            for (chunk in chunks) {
                chunk.copyInto(output, destinationOffset = position)
                position += chunk.size
            }
            return output
        }
    }
}
