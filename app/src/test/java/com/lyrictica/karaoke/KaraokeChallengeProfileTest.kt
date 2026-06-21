package com.lyrictica.karaoke

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeChallengeProfileTest {

    @Test
    fun `easy keeps the artist voice in the mix`() {
        assertTrue(KaraokeChallengeProfile.EASY.keepsArtistVoice)
        assertFalse(KaraokeChallengeProfile.EASY.requiresPreparedBackingTrack)
        assertFalse(KaraokeChallengeProfile.EASY.hidesLyricsUntilMatched)
    }

    @Test
    fun `voiceless removes vocals but keeps lyrics visible`() {
        assertFalse(KaraokeChallengeProfile.VOICELESS.keepsArtistVoice)
        assertTrue(KaraokeChallengeProfile.VOICELESS.requiresPreparedBackingTrack)
        assertFalse(KaraokeChallengeProfile.VOICELESS.hidesLyricsUntilMatched)
    }

    @Test
    fun `hard removes vocals and reveals lyrics only while the player locks in`() {
        assertFalse(KaraokeChallengeProfile.HARD.keepsArtistVoice)
        assertTrue(KaraokeChallengeProfile.HARD.requiresPreparedBackingTrack)
        assertTrue(KaraokeChallengeProfile.HARD.hidesLyricsUntilMatched)
    }
}
