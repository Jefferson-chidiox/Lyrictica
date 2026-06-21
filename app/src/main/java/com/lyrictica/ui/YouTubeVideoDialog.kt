package com.lyrictica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lyrictica.video.YouTubeVideoCandidate
import com.lyrictica.video.YouTubeVideoService
import com.lyrictica.video.toYouTubeSearchQuery
import com.lyrictica.visualizer.VisualizerPalette
import com.oss.euphoriae.data.model.Song
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private sealed interface YouTubeVideoLookupState {
    data object Loading : YouTubeVideoLookupState
    data object MissingApiKey : YouTubeVideoLookupState
    data object NoSong : YouTubeVideoLookupState
    data class Ready(val candidates: List<YouTubeVideoCandidate>) : YouTubeVideoLookupState
    data class Empty(val query: String) : YouTubeVideoLookupState
    data class Error(val message: String) : YouTubeVideoLookupState
}

@Composable
internal fun YouTubeVideoDialog(
    song: Song?,
    theme: VisualizerPalette,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val videoService = remember(context.cacheDir) { YouTubeVideoService(cacheDirectory = context.cacheDir) }
    DisposableEffect(videoService) {
        onDispose {
            videoService.close()
        }
    }

    val lookupState by produceState<YouTubeVideoLookupState>(
        initialValue = when {
            song == null -> YouTubeVideoLookupState.NoSong
            !videoService.isConfigured -> YouTubeVideoLookupState.MissingApiKey
            else -> YouTubeVideoLookupState.Loading
        },
        key1 = song?.id,
        key2 = videoService
    ) {
        value = when {
            song == null -> YouTubeVideoLookupState.NoSong
            !videoService.isConfigured -> YouTubeVideoLookupState.MissingApiKey
            else -> runCatching {
                val results = videoService.searchVideos(song)
                if (results.isEmpty()) {
                    YouTubeVideoLookupState.Empty(song.toYouTubeSearchQuery())
                } else {
                    YouTubeVideoLookupState.Ready(results)
                }
            }.getOrElse { error ->
                YouTubeVideoLookupState.Error(error.message ?: "Could not load YouTube results")
            }
        }
    }

    val results = (lookupState as? YouTubeVideoLookupState.Ready)?.candidates.orEmpty()
    var selectedVideoId by remember(song?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(results) {
        if (results.isNotEmpty() && results.none { it.videoId == selectedVideoId }) {
            selectedVideoId = results.first().videoId
        }
    }

    val selectedCandidate = results.firstOrNull { it.videoId == selectedVideoId } ?: results.firstOrNull()
    val title = song?.title?.takeIf { it.isNotBlank() } ?: "Video"
    val subtitle = song?.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
        ?: "Official YouTube lookup"
    val searchQuery = song?.toYouTubeSearchQuery().orEmpty()

    FeatureDialogShell(
        theme = theme,
        icon = Icons.Default.PlayCircle,
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        actions = {
            when {
                selectedCandidate != null -> {
                    TextButton(onClick = {
                        openExternalYouTube(
                            context = context,
                            uri = Uri.parse("https://www.youtube.com/watch?v=${selectedCandidate.videoId}")
                        )
                    }) {
                        Text("Open in YouTube")
                    }
                }

                searchQuery.isNotBlank() -> {
                    TextButton(onClick = {
                        openExternalYouTube(
                            context = context,
                            uri = buildYouTubeSearchUri(searchQuery)
                        )
                    }) {
                        Text("Search")
                    }
                }
            }
        }
    ) {
        when (lookupState) {
            YouTubeVideoLookupState.Loading -> {
                LoadingState(
                    theme = theme,
                    text = "Finding the best official match..."
                )
            }

            YouTubeVideoLookupState.NoSong -> {
                EmptyState(
                    theme = theme,
                    icon = Icons.Default.Search,
                    title = "Select a track first",
                    message = "Pick a song to resolve its official YouTube video."
                )
            }

            YouTubeVideoLookupState.MissingApiKey -> {
                EmptyState(
                    theme = theme,
                    icon = Icons.Default.Search,
                    title = "YouTube API key needed",
                    message = "Set YOUTUBE_DATA_API_KEY to enable compliant lookup, then this panel will load the best official match."
                )
            }

            is YouTubeVideoLookupState.Empty -> {
                val empty = lookupState as YouTubeVideoLookupState.Empty
                EmptyState(
                    theme = theme,
                    icon = Icons.Default.Search,
                    title = "No direct match yet",
                    message = "Try a broader YouTube search for: ${empty.query}"
                )
            }

            is YouTubeVideoLookupState.Error -> {
                val error = lookupState as YouTubeVideoLookupState.Error
                EmptyState(
                    theme = theme,
                    icon = Icons.Default.Search,
                    title = "Could not load videos",
                    message = error.message
                )
            }

            is YouTubeVideoLookupState.Ready -> {
                val ready = lookupState as YouTubeVideoLookupState.Ready
                val current = selectedCandidate ?: ready.candidates.firstOrNull()

                if (current != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val playerHeight = maxOf(maxWidth * (9f / 16f), 220.dp)
                            YouTubeEmbeddedPlayer(
                                videoId = current.videoId,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(playerHeight)
                            )
                        }

                        Text(
                            text = current.title,
                            color = theme.controlText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = current.channelTitle,
                            color = theme.mutedText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (ready.candidates.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "More matches",
                                    color = theme.controlText.copy(alpha = 0.88f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp
                                )

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(ready.candidates, key = { it.videoId }) { candidate ->
                                        VideoCandidateCard(
                                            candidate = candidate,
                                            selected = candidate.videoId == current.videoId,
                                            theme = theme,
                                            onClick = { selectedVideoId = candidate.videoId }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun YouTubeVideoPanel(
    song: Song?,
    theme: VisualizerPalette,
    modifier: Modifier = Modifier,
    onPlaybackChanged: (isPlaying: Boolean, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val videoService = remember(context.cacheDir) { YouTubeVideoService(cacheDirectory = context.cacheDir) }
    DisposableEffect(videoService) {
        onDispose {
            videoService.close()
        }
    }

    val lookupState by produceState<YouTubeVideoLookupState>(
        initialValue = when {
            song == null -> YouTubeVideoLookupState.NoSong
            !videoService.isConfigured -> YouTubeVideoLookupState.MissingApiKey
            else -> YouTubeVideoLookupState.Loading
        },
        key1 = song?.id,
        key2 = videoService
    ) {
        value = when {
            song == null -> YouTubeVideoLookupState.NoSong
            !videoService.isConfigured -> YouTubeVideoLookupState.MissingApiKey
            else -> runCatching {
                val results = videoService.searchVideos(song)
                if (results.isEmpty()) {
                    YouTubeVideoLookupState.Empty(song.toYouTubeSearchQuery())
                } else {
                    YouTubeVideoLookupState.Ready(results)
                }
            }.getOrElse { error ->
                YouTubeVideoLookupState.Error(error.message ?: "Could not load YouTube results")
            }
        }
    }

    val results = (lookupState as? YouTubeVideoLookupState.Ready)?.candidates.orEmpty()
    var selectedVideoId by remember(song?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(results) {
        if (results.isNotEmpty() && results.none { it.videoId == selectedVideoId }) {
            selectedVideoId = results.first().videoId
        }
    }

    val selectedCandidate = results.firstOrNull { it.videoId == selectedVideoId } ?: results.firstOrNull()
    val searchQuery = song?.toYouTubeSearchQuery().orEmpty()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp),
            horizontalArrangement = Arrangement.End
        ) {
            when {
                selectedCandidate != null -> {
                    TextButton(onClick = {
                        openExternalYouTube(
                            context = context,
                            uri = Uri.parse("https://www.youtube.com/watch?v=${selectedCandidate.videoId}")
                        )
                    }) {
                        Text("Open in YouTube")
                    }
                }

                searchQuery.isNotBlank() -> {
                    TextButton(onClick = {
                        openExternalYouTube(
                            context = context,
                            uri = buildYouTubeSearchUri(searchQuery)
                        )
                    }) {
                        Text("Search")
                    }
                }
            }
        }

        when (lookupState) {
            YouTubeVideoLookupState.Loading -> {
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    LoadingState(
                        theme = theme,
                        text = "Finding the best official match..."
                    )
                }
            }

            YouTubeVideoLookupState.NoSong -> {
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    EmptyState(
                        theme = theme,
                        icon = Icons.Default.Search,
                        title = "Select a track first",
                        message = "Pick a song to resolve its official YouTube video."
                    )
                }
            }

            YouTubeVideoLookupState.MissingApiKey -> {
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    EmptyState(
                        theme = theme,
                        icon = Icons.Default.Search,
                        title = "YouTube API key needed",
                        message = "Set YOUTUBE_DATA_API_KEY to enable compliant lookup, then this panel will load the best official match."
                    )
                }
            }

            is YouTubeVideoLookupState.Empty -> {
                val empty = lookupState as YouTubeVideoLookupState.Empty
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    EmptyState(
                        theme = theme,
                        icon = Icons.Default.Search,
                        title = "No direct match yet",
                        message = "Try a broader YouTube search for: ${empty.query}"
                    )
                }
            }

            is YouTubeVideoLookupState.Error -> {
                val error = lookupState as YouTubeVideoLookupState.Error
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    EmptyState(
                        theme = theme,
                        icon = Icons.Default.Search,
                        title = "Could not load videos",
                        message = error.message
                    )
                }
            }

            is YouTubeVideoLookupState.Ready -> {
                val ready = lookupState as YouTubeVideoLookupState.Ready
                val current = selectedCandidate ?: ready.candidates.firstOrNull()

                if (current != null) {
                    Column(
                        modifier = Modifier.padding(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val playerHeight = maxOf(maxWidth * (9f / 16f), 248.dp)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp)),
                                shape = RoundedCornerShape(26.dp),
                                color = Color.Black,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                tonalElevation = 0.dp,
                                shadowElevation = 10.dp
                            ) {
                                YouTubeEmbeddedPlayer(
                                    videoId = current.videoId,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(playerHeight),
                                    onPlaybackChanged = onPlaybackChanged
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = current.title,
                                color = theme.controlText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = current.channelTitle,
                                color = theme.mutedText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (ready.candidates.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "More matches",
                                    color = theme.controlText.copy(alpha = 0.88f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp,
                                    modifier = Modifier.padding(horizontal = 18.dp)
                                )

                                LazyRow(
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(ready.candidates, key = { it.videoId }) { candidate ->
                                        VideoCandidateCard(
                                            candidate = candidate,
                                            selected = candidate.videoId == current.videoId,
                                            theme = theme,
                                            onClick = { selectedVideoId = candidate.videoId }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(
    theme: VisualizerPalette,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = theme.sliderActiveTrack
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = text,
                color = theme.controlText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EmptyState(
    theme: VisualizerPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(theme.sliderActiveTrack.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = theme.sliderActiveTrack,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = theme.controlText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = message,
                        color = theme.mutedText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCandidateCard(
    candidate: YouTubeVideoCandidate,
    selected: Boolean,
    theme: VisualizerPalette,
    onClick: () -> Unit
) {
    val borderColor = if (selected) theme.sliderActiveTrack.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f)
    val backgroundColor = if (selected) theme.sliderActiveTrack.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)

    Surface(
        modifier = Modifier
            .width(172.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp)
                    .background(Color.Black.copy(alpha = 0.28f))
            ) {
                if (!candidate.thumbnailUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(candidate.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = candidate.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = theme.sliderActiveTrack
                                )
                            }
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = theme.sliderActiveTrack.copy(alpha = 0.90f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = candidate.title,
                    color = theme.controlText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = candidate.channelTitle,
                    color = theme.mutedText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class EmbeddedYouTubePlayback(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

@Composable
private fun YouTubeEmbeddedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onPlaybackChanged: (isPlaying: Boolean, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val onPlaybackChangedState by rememberUpdatedState(onPlaybackChanged)
    var playback by remember { mutableStateOf(EmbeddedYouTubePlayback()) }
    var fullscreenBrowserUri by remember { mutableStateOf<Uri?>(null) }
    val appReferrer = remember(context.packageName) { buildYouTubeAppReferrer(context.packageName) }
    val origin = remember(appReferrer) { buildYouTubeOrigin(appReferrer) }
    val html = remember(videoId, origin, appReferrer) {
        buildYouTubeEmbedHtml(
            videoId = videoId,
            origin = origin,
            widgetReferrer = appReferrer
        )
    }
    val bridge = remember {
        YouTubePlayerBridge(
            onPlayerReady = {
                playback = playback.copy(
                    isReady = true,
                    errorMessage = null
                )
            },
            onPlaybackState = { state ->
                playback = playback.copy(
                    isPlaying = state == YOUTUBE_PLAYER_STATE_PLAYING
                )
            },
            onPlaybackProgress = { positionMs, durationMs ->
                playback = playback.copy(
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            },
            onPlayerError = { errorCode ->
                playback = playback.copy(
                    isReady = false,
                    isPlaying = false,
                    errorMessage = mapYouTubePlayerError(errorCode)
                )
            },
            onJavaScriptError = { message ->
                playback = playback.copy(
                    isReady = false,
                    isPlaying = false,
                    errorMessage = message.ifBlank {
                        "Could not start the YouTube player inside the app."
                    }
                )
            }
        )
    }

    LaunchedEffect(videoId) {
        playback = EmbeddedYouTubePlayback()
        fullscreenBrowserUri = null
        onPlaybackChangedState(false, 0L, 0L)
    }

    LaunchedEffect(videoId, playback.isReady, playback.errorMessage) {
        if (playback.isReady || playback.errorMessage != null) {
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(8_000L)
        if (!playback.isReady && playback.errorMessage == null) {
            playback = playback.copy(
                errorMessage = "The YouTube player did not become ready. Try another match or open it in YouTube."
            )
        }
    }

    LaunchedEffect(playback) {
        onPlaybackChangedState(playback.isPlaying, playback.positionMs, playback.durationMs)
    }

    DisposableEffect(Unit) {
        onDispose {
            onPlaybackChangedState(false, 0L, 0L)
            webViewRef.value?.let { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.removeJavascriptInterface(YOUTUBE_PLAYER_BRIDGE_NAME)
                webView.webChromeClient = null
                (webView.parent as? ViewGroup)?.removeView(webView)
            }
            webViewRef.value = null
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).also { webView ->
                    webView.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewRef.value = webView
                    configureYouTubeWebView(
                        webView = webView,
                        context = viewContext,
                        activity = activity,
                        bridge = bridge,
                        onOpenInAppUrl = { uri ->
                            fullscreenBrowserUri = uri
                        },
                        onPageError = { message ->
                            playback = playback.copy(
                                isReady = false,
                                isPlaying = false,
                                errorMessage = message
                            )
                        }
                    )
                    webView.tag = videoId
                    webView.loadDataWithBaseURL(
                        appReferrer,
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                val currentVideoId = webView.tag as? String
                if (currentVideoId != videoId) {
                    playback = EmbeddedYouTubePlayback()
                    webView.tag = videoId
                    webView.loadDataWithBaseURL(
                        appReferrer,
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )

        when {
            playback.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.64f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playback.errorMessage.orEmpty(),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            !playback.isReady -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.86f))
                }
            }
        }
    }

    fullscreenBrowserUri?.let { uri ->
        FullscreenWebViewDialog(
            initialUri = uri,
            onDismiss = { fullscreenBrowserUri = null }
        )
    }
}

@Composable
private fun FullscreenWebViewDialog(
    initialUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var currentUri by remember(initialUri) { mutableStateOf(initialUri) }
    val hostLabel = currentUri.host?.removePrefix("www.") ?: currentUri.toString()

    BackHandler {
        val webView = webViewRef.value
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { webView ->
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = WebViewClient()
                webView.destroy()
            }
            webViewRef.value = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val webView = webViewRef.value
                            if (webView?.canGoBack() == true) {
                                webView.goBack()
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Continue in YouTube",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = hostLabel,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(onClick = { openExternalUri(context, currentUri) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in browser",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browser")
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { viewContext ->
                        WebView(viewContext).also { webView ->
                            webView.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewRef.value = webView
                            configureFullscreenWebView(
                                webView = webView,
                                context = viewContext,
                                onUrlChange = { uri -> currentUri = uri }
                            )
                            webView.loadUrl(currentUri.toString())
                        }
                    },
                    update = { webView ->
                        val target = currentUri.toString()
                        if (webView.url != target) {
                            webView.loadUrl(target)
                        }
                    }
                )
            }
        }
    }
}

private fun configureFullscreenWebView(
    webView: WebView,
    context: Context,
    onUrlChange: (Uri) -> Unit
) {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

    webView.setBackgroundColor(AndroidColor.BLACK)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.mediaPlaybackRequiresUserGesture = false
    webView.settings.javaScriptCanOpenWindowsAutomatically = true
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = true
    webView.settings.setSupportMultipleWindows(true)
    webView.settings.builtInZoomControls = false
    webView.settings.displayZoomControls = false
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false
    webView.overScrollMode = View.OVER_SCROLL_NEVER
    webView.webChromeClient = FullscreenBrowserChromeClient(
        context = context,
        onUrlChange = onUrlChange
    )
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            return if (uri.isSupportedWebUri()) {
                false
            } else {
                openExternalUri(context, uri)
                true
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let {
                onUrlChange(Uri.parse(it))
            }
        }
    }
}

private class FullscreenBrowserChromeClient(
    private val context: Context,
    private val onUrlChange: (Uri) -> Unit
) : WebChromeClient() {
    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val popupWebView = WebView(view?.context ?: context)
        popupWebView.settings.javaScriptEnabled = true
        popupWebView.settings.domStorageEnabled = true
        popupWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        popupWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                handlePopupUri(uri)
                view?.stopLoading()
                view?.destroy()
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val uri = url?.let(Uri::parse) ?: return
                handlePopupUri(uri)
                view?.stopLoading()
                view?.destroy()
            }

            private fun handlePopupUri(uri: Uri) {
                if (uri.isSupportedWebUri()) {
                    onUrlChange(uri)
                } else {
                    openExternalUri(context, uri)
                }
            }
        }
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }
}

private class YouTubePlayerBridge(
    private val onPlayerReady: () -> Unit,
    private val onPlaybackState: (Int) -> Unit,
    private val onPlaybackProgress: (Long, Long) -> Unit,
    private val onPlayerError: (Int) -> Unit,
    private val onJavaScriptError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post(onPlayerReady)
    }

    @JavascriptInterface
    fun onPlaybackState(state: Int) {
        mainHandler.post {
            onPlaybackState(state)
        }
    }

    @JavascriptInterface
    fun onPlaybackProgress(currentSeconds: Double, durationSeconds: Double) {
        val positionMs = (currentSeconds * 1000.0).toLong().coerceAtLeast(0L)
        val durationMs = (durationSeconds * 1000.0).toLong().coerceAtLeast(0L)
        mainHandler.post {
            onPlaybackProgress(positionMs, durationMs)
        }
    }

    @JavascriptInterface
    fun onPlayerError(errorCode: Int) {
        mainHandler.post {
            onPlayerError(errorCode)
        }
    }

    @JavascriptInterface
    fun onJavaScriptError(message: String?) {
        mainHandler.post {
            onJavaScriptError(message.orEmpty())
        }
    }
}

private fun configureYouTubeWebView(
    webView: WebView,
    context: Context,
    activity: Activity?,
    bridge: YouTubePlayerBridge,
    onOpenInAppUrl: (Uri) -> Unit,
    onPageError: (String) -> Unit
) {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    webView.setBackgroundColor(AndroidColor.TRANSPARENT)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.mediaPlaybackRequiresUserGesture = false
    webView.settings.javaScriptCanOpenWindowsAutomatically = true
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = true
    webView.settings.setSupportMultipleWindows(true)
    webView.settings.builtInZoomControls = false
    webView.settings.displayZoomControls = false
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false
    webView.isFocusable = true
    webView.isFocusableInTouchMode = true
    webView.overScrollMode = View.OVER_SCROLL_NEVER
    webView.addJavascriptInterface(bridge, YOUTUBE_PLAYER_BRIDGE_NAME)
    webView.webChromeClient = if (activity != null) {
        YouTubeWebChromeClient(
            activity = activity,
            fallbackContext = context,
            onOpenInAppUrl = onOpenInAppUrl
        )
    } else {
        WebChromeClient()
    }
    webView.webViewClient = YouTubeWebViewClient(
        context = context,
        onOpenInAppUrl = onOpenInAppUrl,
        onPageError = onPageError
    )
}

internal fun buildYouTubeEmbedHtml(
    videoId: String,
    origin: String,
    widgetReferrer: String
): String {
    return """
        <!DOCTYPE html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <meta name="referrer" content="strict-origin-when-cross-origin" />
            <style>
              html, body {
                margin: 0;
                padding: 0;
                width: 100%;
                height: 100%;
                background: #000000;
                overflow: hidden;
              }
              #player-shell,
              #player {
                position: absolute;
                inset: 0;
                width: 100%;
                height: 100%;
              }
              iframe {
                border: 0;
                width: 100% !important;
                height: 100% !important;
                position: absolute;
                top: 0;
                left: 0;
              }
            </style>
          </head>
          <body>
            <div id="player-shell">
              <div id="player"></div>
            </div>
            <script>
              var tag = document.createElement('script');
              tag.src = 'https://www.youtube.com/iframe_api';
              var firstScriptTag = document.getElementsByTagName('script')[0];
              firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

              var player = null;
              var progressTimer = null;

              function bridgeMethod(name) {
                if (!window.$YOUTUBE_PLAYER_BRIDGE_NAME) {
                  return null;
                }
                var method = window.$YOUTUBE_PLAYER_BRIDGE_NAME[name];
                return typeof method === 'function' ? method.bind(window.$YOUTUBE_PLAYER_BRIDGE_NAME) : null;
              }

              function reportReady() {
                var callback = bridgeMethod('onPlayerReady');
                if (callback) {
                  callback();
                }
              }

              function reportState(state) {
                var callback = bridgeMethod('onPlaybackState');
                if (callback) {
                  callback(state);
                }
              }

              function reportError(code) {
                var callback = bridgeMethod('onPlayerError');
                if (callback) {
                  callback(code || 0);
                }
              }

              function reportJavaScriptError(message) {
                var callback = bridgeMethod('onJavaScriptError');
                if (callback) {
                  callback(message || 'Could not start the YouTube player inside the app.');
                }
              }

              function reportProgress() {
                if (!player || typeof player.getCurrentTime !== 'function') {
                  return;
                }
                var callback = bridgeMethod('onPlaybackProgress');
                if (callback) {
                  callback(
                    player.getCurrentTime() || 0,
                    player.getDuration() || 0
                  );
                }
              }

              function stopProgressTimer() {
                if (progressTimer !== null) {
                  window.clearInterval(progressTimer);
                  progressTimer = null;
                }
              }

              function startProgressTimer() {
                stopProgressTimer();
                progressTimer = window.setInterval(reportProgress, 250);
                reportProgress();
              }

              function onPlayerReady(event) {
                reportReady();
                reportProgress();
              }

              function onPlayerStateChange(event) {
                reportState(event.data);
                if (event.data === YT.PlayerState.PLAYING) {
                  startProgressTimer();
                } else {
                  stopProgressTimer();
                  reportProgress();
                }
              }

              function onPlayerError(event) {
                stopProgressTimer();
                reportError(event && typeof event.data === 'number' ? event.data : 0);
              }

              function onYouTubeIframeAPIReady() {
                if (!window.YT || typeof window.YT.Player !== 'function') {
                  reportJavaScriptError('The YouTube iframe API did not initialize.');
                  return;
                }
                player = new YT.Player('player', {
                  width: '100%',
                  height: '100%',
                  videoId: '$videoId',
                  playerVars: {
                    playsinline: 1,
                    controls: 1,
                    fs: 1,
                    autoplay: 1,
                    enablejsapi: 1,
                    origin: '$origin',
                    widget_referrer: '$widgetReferrer'
                  },
                  events: {
                    'onReady': onPlayerReady,
                    'onStateChange': onPlayerStateChange,
                    'onError': onPlayerError
                  }
                });
              }

              window.addEventListener('error', function(event) {
                reportJavaScriptError(event && event.message ? event.message : 'Could not start the YouTube player inside the app.');
              });

              window.addEventListener('unhandledrejection', function(event) {
                var reason = event && event.reason ? String(event.reason) : 'Could not start the YouTube player inside the app.';
                reportJavaScriptError(reason);
              });
            </script>
          </body>
        </html>
    """.trimIndent()
}

private class YouTubeWebViewClient(
    private val context: Context,
    private val onOpenInAppUrl: (Uri) -> Unit,
    private val onPageError: (String) -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        if (request.hasGesture() && uri.isSupportedWebUri()) {
            onOpenInAppUrl(uri)
            return true
        }
        if (!uri.isSupportedWebUri()) {
            openExternalUri(context, uri)
            return true
        }
        return false
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onPageError(error?.description?.toString() ?: "Could not load the YouTube player.")
        }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && errorResponse?.statusCode != null && errorResponse.statusCode >= 400) {
            onPageError("Could not load the YouTube player (${errorResponse.statusCode}).")
        }
    }
}

@Suppress("DEPRECATION")
private class YouTubeWebChromeClient(
    private val activity: Activity,
    private val fallbackContext: Context,
    private val onOpenInAppUrl: (Uri) -> Unit
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    private val decorView: ViewGroup
        get() = activity.window.decorView as ViewGroup

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        decorView.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        decorView.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val popupWebView = WebView(view?.context ?: fallbackContext)
        popupWebView.settings.javaScriptEnabled = true
        popupWebView.settings.domStorageEnabled = true
        popupWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        popupWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                handlePopupUri(uri)
                view?.stopLoading()
                view?.destroy()
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val uri = url?.let(Uri::parse) ?: return
                handlePopupUri(uri)
                view?.stopLoading()
                view?.destroy()
            }

            private fun handlePopupUri(uri: Uri) {
                if (uri.isSupportedWebUri()) {
                    onOpenInAppUrl(uri)
                } else {
                    openExternalUri(fallbackContext, uri)
                }
            }
        }
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun Uri.isSupportedWebUri(): Boolean {
    val normalizedScheme = scheme?.lowercase().orEmpty()
    return normalizedScheme == "http" || normalizedScheme == "https"
}

private fun openExternalUri(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openExternalYouTube(context: Context, uri: Uri) {
    openExternalUri(context, uri)
}

private fun buildYouTubeSearchUri(query: String): Uri {
    val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    return Uri.parse("https://www.youtube.com/results?search_query=$encoded")
}

internal fun buildYouTubeAppReferrer(packageName: String): String =
    "https://${packageName.trim().trimEnd('/')}/"

internal fun buildYouTubeOrigin(appReferrer: String): String = appReferrer.trim().trimEnd('/')

internal fun mapYouTubePlayerError(errorCode: Int): String = when (errorCode) {
    2 -> "The selected video id was rejected by YouTube. Try another match."
    5 -> "YouTube could not start this video in the embedded player."
    100 -> "That video is no longer available on YouTube."
    101, 150 -> "The video owner does not allow this match to play inside other apps."
    153 -> "YouTube could not verify this app as the embed referrer for that video."
    else -> "Could not start this YouTube video inside the app. Try another match or open it in YouTube."
}

private const val YOUTUBE_PLAYER_BRIDGE_NAME = "LyricticaBridge"
private const val YOUTUBE_PLAYER_STATE_PLAYING = 1
