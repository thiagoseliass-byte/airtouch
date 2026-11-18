package com.airtouch.system

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Ações de sistema que não exigem acessibilidade (volume e mídia). */
class SystemActions(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volume(up: Boolean) {
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    fun playPause() {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
