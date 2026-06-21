package com.lyrictica.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeEmbedConfigTest {
    @Test
    fun buildsAppReferrerFromPackageName() {
        assertEquals(
            "https://com.lyrictica/",
            buildYouTubeAppReferrer("com.lyrictica")
        )
    }

    @Test
    fun derivesOriginFromReferrerUrl() {
        assertEquals(
            "https://com.lyrictica",
            buildYouTubeOrigin("https://com.lyrictica/")
        )
    }

    @Test
    fun embedHtmlKeepsExplicitPlayerConfig() {
        val html = buildYouTubeEmbedHtml(
            videoId = "abc123XYZ",
            origin = "https://com.lyrictica",
            widgetReferrer = "https://com.lyrictica/"
        )

        assertTrue(html.contains("videoId: 'abc123XYZ'"))
        assertTrue(html.contains("controls: 1"))
        assertTrue(html.contains("enablejsapi: 1"))
        assertTrue(html.contains("origin: 'https://com.lyrictica'"))
        assertTrue(html.contains("widget_referrer: 'https://com.lyrictica/'"))
        assertTrue(html.contains("onPlayerError"))
        assertTrue(html.contains("onPlayerReady"))
    }
}
