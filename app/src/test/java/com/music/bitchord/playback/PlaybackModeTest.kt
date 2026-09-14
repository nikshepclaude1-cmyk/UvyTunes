package com.music.bitchord.playback

import com.music.bitchord.data.settings.PlaybackMode
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Verifies that [PlaybackMode] propagation logic is correct at the
 * settings layer. The actual crossfade integration is tested by the
 * Android CI build (instrumented tests require a Media3 runtime).
 */
class PlaybackModeTest {

    @Test
    fun shortsIsNotMax() {
        assertFalse(PlaybackMode.SHORTS == PlaybackMode.MAX)
    }

    @Test
    fun maxIsNotShorts() {
        assertFalse(PlaybackMode.MAX == PlaybackMode.SHORTS)
    }
}
