package com.lyrictica.audio

/** UI-friendly status for background band analysis work. */
data class AnalysisStatus(
    val stage: Stage = Stage.IDLE,
    val spectrumReady: Boolean = false,
    val message: String = "",
    val error: String? = null
) {
    enum class Stage {
        IDLE,
        STAGING,
        SPECTRUM,
        READY,
        ERROR
    }

    val isBusy: Boolean
        get() = stage == Stage.STAGING || stage == Stage.SPECTRUM
}
