package com.lyrictica.karaoke

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal class KaraokeSpeechRecognizer(
    context: Context,
    private val onCandidatesUpdated: (lineIndex: Int, candidates: List<String>) -> Unit,
    private val onRecognizerIssue: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var activeLineIndex: Int? = null
    private var isListening: Boolean = false
    private val candidatesByLine = linkedMapOf<Int, LinkedHashSet<String>>()
    private val recentFinalPhrases = mutableListOf<String>()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun activateLine(lineIndex: Int) {
        runOnMain {
            if (!isAvailable) {
                onRecognizerIssue("Speech recognition is not available on this device.")
                return@runOnMain
            }
            activeLineIndex = lineIndex
            ensureRecognizer()
            if (!isListening) {
                startListening()
            }
        }
    }

    fun snapshotCandidates(lineIndex: Int): List<String> = candidatesByLine[lineIndex]?.toList().orEmpty()

    fun clearLine(lineIndex: Int) {
        candidatesByLine.remove(lineIndex)
    }

    fun cancelActive() {
        runOnMain {
            activeLineIndex = null
            recentFinalPhrases.clear()
            if (isListening) {
                recognizer?.cancel()
                isListening = false
            }
        }
    }

    fun clearAll() {
        cancelActive()
        candidatesByLine.clear()
    }

    fun release() {
        runOnMain {
            activeLineIndex = null
            candidatesByLine.clear()
            recentFinalPhrases.clear()
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
            isListening = false
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return

        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    storeResults(results, isFinal = true)
                    isListening = false
                    restartIfNeeded()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    storeResults(partialResults, isFinal = false)
                }

                override fun onError(error: Int) {
                    isListening = false
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_CLIENT -> restartIfNeeded()
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            recognizer?.cancel()
                            restartIfNeeded()
                        }
                        else -> {
                            restartIfNeeded()
                            onRecognizerIssue(errorMessageFor(error))
                        }
                    }
                }
            })
        }
    }

    private fun storeResults(results: Bundle?, isFinal: Boolean) {
        val lineIndex = activeLineIndex ?: return
        val phrases = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        if (phrases.isEmpty()) return

        val currentPhrase = phrases.first()
        val combined = if (recentFinalPhrases.isEmpty()) {
            currentPhrase
        } else {
            (recentFinalPhrases + currentPhrase).joinToString(" ")
        }

        if (isFinal) {
            recentFinalPhrases.add(currentPhrase)
            if (recentFinalPhrases.size > 3) {
                recentFinalPhrases.removeAt(0)
            }
        }

        for (i in maxOf(0, lineIndex - 1)..lineIndex) {
            val bucket = candidatesByLine.getOrPut(i) { linkedSetOf() }
            bucket.add(combined)
            phrases.forEach(bucket::add)
            onCandidatesUpdated(i, bucket.toList())
        }
    }

    private fun restartIfNeeded() {
        if (activeLineIndex != null && !isListening) {
            startListening()
        }
    }

    private fun startListening() {
        isListening = true
        recognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
            }
        )
    }

    private fun errorMessageFor(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio could not be processed."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access is needed for challenge mode."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition is currently unavailable."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition is temporarily unavailable."
            else -> "Speech recognition could not continue."
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
