package com.maik205.shoumeiplayer.platform.media

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAudioFocusPlayerEngineTest {
    @Test
    fun `transient and duck losses pause active playback`() {
        assertEquals(
            AudioFocusAction.Pause,
            audioFocusAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, wasPlaying = true, resumeOnFocusGain = false),
        )
        assertEquals(
            AudioFocusAction.Pause,
            audioFocusAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, wasPlaying = true, resumeOnFocusGain = false),
        )
    }

    @Test
    fun `permanent loss pauses and abandons focus`() {
        assertEquals(
            AudioFocusAction.PauseAndAbandon,
            audioFocusAction(AudioManager.AUDIOFOCUS_LOSS, wasPlaying = true, resumeOnFocusGain = true),
        )
    }

    @Test
    fun `gain resumes only an interrupted player`() {
        assertEquals(
            AudioFocusAction.Resume,
            audioFocusAction(AudioManager.AUDIOFOCUS_GAIN, wasPlaying = false, resumeOnFocusGain = true),
        )
        assertEquals(
            AudioFocusAction.Ignore,
            audioFocusAction(AudioManager.AUDIOFOCUS_GAIN, wasPlaying = false, resumeOnFocusGain = false),
        )
    }
}
