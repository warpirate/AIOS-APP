package com.mitra

import android.app.Application
import com.mitra.inference.BrainHolder
import com.mitra.inference.LiteRtBrain
import com.mitra.inference.ModelDownloader
import com.mitra.inference.ModelRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Process-lifetime container for the singleton [BrainHolder]. Activity recreation (rotation,
 * theme change, finish/relaunch, swipe-up + reopen within minutes) no longer reloads the
 * 2.6 GB Gemma 4 E2B model — the brain instance + its conversation KV cache stay alive for
 * as long as the OS keeps the process.
 *
 * Eager prewarm only happens when the model file is already on disk: on a fresh install the
 * file doesn't exist yet, the factory would fail, and [BrainHolder] treats failure as sticky.
 * For the freshly-downloaded path, AppRoot calls prewarm() itself once the user reaches
 * Phase.LOADING.
 */
class MitraApp : Application() {
    lateinit var brainHolder: BrainHolder
        private set

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob())
        val modelFile = File(getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        brainHolder =
            BrainHolder(
                factory = { runCatching { LiteRtBrain(modelFile.absolutePath, cacheDir.path) }.getOrNull() },
                scope = appScope,
            )
        if (ModelDownloader(modelFile).isComplete()) {
            brainHolder.prewarm()
        }
    }
}
