package com.lyrictica.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMetadataExtractorTest {

    @Test
    fun treatsPlaceholderMetadataAsMissing() {
        assertNull(TrackMetadataExtractor.cleanMetadataValue("Unknown"))
        assertNull(TrackMetadataExtractor.cleanMetadataValue("unknown artist"))
        assertNull(TrackMetadataExtractor.cleanMetadataValue("<unknown>"))
        assertNull(TrackMetadataExtractor.cleanMetadataValue("Audius"))
        assertNull(TrackMetadataExtractor.cleanMetadataValue("audius"))
        assertNull(TrackMetadataExtractor.cleanMetadataValue("  Audius  "))
    }

    @Test
    fun preservesRealMetadataValues() {
        assertEquals("Intro", TrackMetadataExtractor.cleanMetadataValue("Intro"))
        assertEquals("Real Artist", TrackMetadataExtractor.cleanMetadataValue("  Real Artist  "))
    }
}
