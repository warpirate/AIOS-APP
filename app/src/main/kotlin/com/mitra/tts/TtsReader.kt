package com.mitra.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wrapper over Android system [TextToSpeech]. Local-only — uses the device's installed TTS engine,
 * no network. Gated by [com.mitra.prefs.UserPrefs.ttsEnabled]; default OFF (opt-in, per the
 * approachability principle — don't ambush users with voice on first launch).
 *
 * Lifecycle: construct once per ChatScreen, [shutdown] on dispose. Speak attempts before the engine
 * finishes initialising are dropped silently; the next reply will succeed.
 */
class TtsReader(
    context: Context,
) {
    private val ready = AtomicBoolean(false)
    private val tts: TextToSpeech =
        TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready.set(true)
            }
        }

    init {
        // Prefer Indian English to match voice.md register; fall back to system default.
        runCatching { tts.language = Locale("en", "IN") }
        runCatching { tts.setSpeechRate(1.0f) }
    }

    fun speak(text: String) {
        if (!ready.get() || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mitra-reply")
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}
