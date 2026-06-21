package com.lyrictica.karaoke

import com.oss.euphoriae.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Direct LALAL.AI API client.
 *
 * Flow:
 * 1. upload source file -> source_id
 * 2. split via stem_separator -> task_id
 * 3. poll check -> result tracks
 * 4. download vocals/no_vocals
 * 5. delete source_id from LALAL storage
 */
internal fun encodeLalalSplitRequest(sourceId: String): String {
    return lalalJson.encodeToString(
        LalalSplitRequest(
            sourceId = sourceId,
            presets = LalalSplitPresets()
        )
    )
}

internal fun extractLalalErrorMessage(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        lalalJson.parseToJsonElement(trimmed).asMessage()
    }.getOrNull() ?: trimmed
}

private fun JsonElement.asMessage(): String? {
    return when (this) {
        is JsonPrimitive -> content.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        is JsonObject -> this["detail"]?.asMessage()
            ?: this["message"]?.asMessage()
            ?: this["msg"]?.asMessage()
            ?: this["error"]?.asMessage()
            ?: toString().takeIf { it.isNotBlank() }
        is JsonArray -> mapNotNull { it.asMessage() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .takeIf { it.isNotBlank() }
        else -> toString().takeIf { it.isNotBlank() }
    }
}

internal class LalalProxyClient : StemSeparationProvider {
    override val providerId: String = "lalal_api"

    override val isConfigured: Boolean
        get() = BuildConfig.WORKER_BASE_URL.isNotBlank()

    override suspend fun submit(source: File): StemJob {
        check(isConfigured) { "Worker base URL is not configured." }

        val upload = client.post("$baseUrl/upload/") {
            header(HttpHeaders.ContentDisposition, "attachment; filename=\"${source.name}\"")
            header(HttpHeaders.ContentType, ContentType.Application.OctetStream)
            setBody(source.readBytes())
        }

        upload.requireSuccess("LALAL upload failed")

        val uploaded = upload.body<LalalUploadResponse>()
        val split = client.post("$baseUrl/split/stem_separator/") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(encodeLalalSplitRequest(uploaded.id))
        }

        split.requireSuccess("LALAL split failed")

        val splitResponse = split.body<LalalSplitResponse>()
        return StemJob(
            jobId = splitResponse.taskId,
            sourceId = uploaded.id,
            acceptedMessage = "LALAL stem split accepted"
        )
    }

    override suspend fun poll(jobId: String): StemJobStatus {
        check(isConfigured) { "Worker base URL is not configured." }

        val response = client.post("$baseUrl/check/") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(LalalCheckRequest(taskIds = listOf(jobId)))
        }

        response.requireSuccess("LALAL check failed")

        val payload = response.body<LalalCheckResponse>()
        val task = payload.result[jobId] ?: throw IllegalStateException("LALAL did not return task $jobId")
        return task.toStemJobStatus(jobId)
    }

    override suspend fun download(jobId: String, targetDir: File): StemBundle {
        val status = poll(jobId)
        if (status.phase != StemJobPhase.SUCCEEDED) {
            throw IllegalStateException(status.failureReason ?: "LALAL task is not ready yet")
        }

        val vocalsUrl = status.vocalsUrl
            ?: throw IllegalStateException("LALAL task did not return a vocals URL")
        val instrumentalUrl = status.instrumentalUrl
            ?: throw IllegalStateException("LALAL task did not return an instrumental URL")

        targetDir.mkdirs()
        val vocalsFile = downloadToFile(normalizeDownloadUrl(vocalsUrl), File(targetDir, "vocals${extensionFor(vocalsUrl)}"))
        val instrumentalFile = downloadToFile(normalizeDownloadUrl(instrumentalUrl), File(targetDir, "instrumental${extensionFor(instrumentalUrl)}"))

        return StemBundle(
            vocalsFile = vocalsFile,
            instrumentalFile = instrumentalFile,
            sourceId = status.sourceId,
            durationMs = status.durationMs,
            formatHint = vocalsFile.extension.ifBlank { instrumentalFile.extension }
        )
    }

    override suspend fun delete(sourceId: String?) {
        if (!isConfigured || sourceId.isNullOrBlank()) return
        runCatching {
            val response = client.post("$baseUrl/delete/") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(LalalDeleteRequest(sourceId = sourceId))
            }
            response.requireSuccess("LALAL delete failed")
        }
    }

    private suspend fun downloadToFile(url: String, target: File): File {
        val response = client.get(url)
        response.requireSuccess("LALAL download failed")
        val bytes = response.body<ByteArray>()
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    private fun normalizeDownloadUrl(url: String): String {
        return if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url
    }

    private fun extensionFor(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "")
        return ext.takeIf { it.isNotBlank() }?.lowercase()?.let { ".${it}" } ?: ".m4a"
    }

    private fun LalalTaskResponse.toStemJobStatus(jobId: String): StemJobStatus {
        val tracks = result?.tracks.orEmpty()
        val vocalsTrack = tracks.firstOrNull { track ->
            track.label?.equals("vocals", ignoreCase = true) == true ||
                track.label?.contains("vocal", ignoreCase = true) == true
        }
        val instrumentalTrack = tracks.firstOrNull { track ->
            track.label?.equals("no_vocals", ignoreCase = true) == true ||
                track.label?.contains("instrumental", ignoreCase = true) == true ||
                track.type?.equals("back", ignoreCase = true) == true
        }
        val failure = failureMessage()
        return StemJobStatus(
            jobId = jobId,
            phase = status.toPhase(),
            sourceId = sourceId,
            progressPercent = progress,
            statusMessage = statusMessage(),
            failureReason = failure,
            vocalsUrl = vocalsTrack?.url,
            instrumentalUrl = instrumentalTrack?.url,
            durationMs = result?.duration?.let { durationSeconds -> durationSeconds * 1000L }
        )
    }

    private fun LalalTaskResponse.statusMessage(): String? {
        return when {
            status.equals("progress", ignoreCase = true) && progress != null -> "Processing ($progress%)"
            status.equals("success", ignoreCase = true) -> "Stem split ready"
            else -> failureMessage() ?: status
        }
    }

    private fun LalalTaskResponse.failureMessage(): String? {
        return error?.asMessage()
    }

    private suspend fun HttpResponse.requireSuccess(context: String) {
        if (status.isSuccess()) return
        val detail = runCatching { bodyAsText() }
            .getOrNull()
            ?.let(::extractLalalErrorMessage)
            ?.takeIf { it.isNotBlank() }
        val suffix = detail?.let { ": $it" } ?: ""
        throw IllegalStateException("$context with HTTP ${status.value}$suffix")
    }

    private fun String.toPhase(): StemJobPhase {
        return when (lowercase()) {
            "queued", "pending" -> StemJobPhase.QUEUED
            "progress", "processing", "running", "working" -> StemJobPhase.PROCESSING
            "success", "succeeded", "ready", "completed" -> StemJobPhase.SUCCEEDED
            else -> StemJobPhase.FAILED
        }
    }

    companion object {
        private val baseUrl = "${BuildConfig.WORKER_BASE_URL}/lalal"
        private val client = HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(120, TimeUnit.SECONDS)
                    writeTimeout(120, TimeUnit.SECONDS)
                }
            }
            install(ContentNegotiation) {
                json(lalalJson)
            }
        }
    }
}

private val lalalJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
private data class LalalUploadResponse(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("duration") val duration: Long? = null,
    @SerialName("expires") val expires: String? = null
)

@Serializable
private data class LalalSplitRequest(
    @SerialName("source_id") val sourceId: String,
    @SerialName("presets") val presets: LalalSplitPresets
)

@Serializable
private data class LalalSplitPresets(
    @SerialName("stem") val stem: String = "vocals",
    @SerialName("extraction_level") val extractionLevel: String = "deep_extraction",
    @SerialName("splitter") val splitter: String = "auto",
    @SerialName("dereverb_enabled") val dereverbEnabled: Boolean = false,
    @SerialName("multivocal") val multivocal: String? = null
)

@Serializable
private data class LalalSplitResponse(
    @SerialName("task_id") val taskId: String
)

@Serializable
private data class LalalCheckRequest(
    @SerialName("task_ids") val taskIds: List<String>
)

@Serializable
private data class LalalCheckResponse(
    val result: Map<String, LalalTaskResponse> = emptyMap()
)

@Serializable
private data class LalalTaskResponse(
    val status: String,
    @SerialName("source_id") val sourceId: String? = null,
    val progress: Int? = null,
    val result: LalalTaskResult? = null,
    val error: JsonElement? = null
)

@Serializable
private data class LalalTaskResult(
    val duration: Long? = null,
    val tracks: List<LalalTaskTrack> = emptyList()
)

@Serializable
private data class LalalTaskTrack(
    val label: String? = null,
    val type: String? = null,
    val url: String? = null
)

@Serializable
private data class LalalDeleteRequest(
    @SerialName("source_id") val sourceId: String
)
