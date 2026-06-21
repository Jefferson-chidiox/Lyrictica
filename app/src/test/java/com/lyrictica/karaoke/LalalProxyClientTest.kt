package com.lyrictica.karaoke

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LalalProxyClientTest {

    @Test
    fun encodeLalalSplitRequest_includesRequiredStemField() {
        val payload = encodeLalalSplitRequest("123e4567-e89b-12d3-a456-426614174000")

        assertTrue(payload.contains("\"source_id\":\"123e4567-e89b-12d3-a456-426614174000\""))
        assertTrue(payload.contains("\"stem\":\"vocals\""))
        assertTrue(payload.contains("\"extraction_level\":\"deep_extraction\""))
        assertTrue(payload.contains("\"splitter\":\"auto\""))
        assertTrue(payload.contains("\"dereverb_enabled\":false"))
    }

    @Test
    fun extractLalalErrorMessage_readsValidationMessage() {
        val message = extractLalalErrorMessage(
            """{"detail":[{"type":"value_error","loc":["body","split_input","presets"],"msg":"Value error, Missing required stem field: one of 'stem', 'stem_list', or 'stem_option' must be provided"}]}"""
        )

        assertNotNull(message)
        assertTrue(message!!.contains("Missing required stem field"))
    }
}
