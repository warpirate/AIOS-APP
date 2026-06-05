package com.mitra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mitra.agent.AgentLoop
import com.mitra.agent.IntentParser
import com.mitra.inference.LiteRtBrain
import com.mitra.inference.ModelDownloader
import com.mitra.inference.ModelRegistry
import com.mitra.tools.ToolRegistry
import com.mitra.ui.ChatScreen
import com.mitra.ui.DownloadScreen
import com.mitra.ui.ErrorScreen
import com.mitra.ui.LoadingBrainScreen
import com.mitra.ui.WelcomeScreen
import com.mitra.ui.theme.MitraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class Phase { BOOT, WELCOME, DOWNLOAD, LOADING, CHAT, ERROR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keyword router stays as the fallback so the flashlight works even with no model.
        val agent = AgentLoop(
            router = IntentParser(),
            tools = ToolRegistry.all(applicationContext),
        )
        // App-private external dir: no storage permission needed; the downloader writes here.
        val modelFile = File(applicationContext.getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        val cacheDir = applicationContext.cacheDir.path
        setContent {
            MitraTheme {
                AppRoot(modelFile, cacheDir, agent)
            }
        }
    }
}

@Composable
private fun AppRoot(modelFile: File, cacheDir: String, agent: AgentLoop) {
    var phase by remember { mutableStateOf(Phase.BOOT) }
    var brain by remember { mutableStateOf<LiteRtBrain?>(null) }

    var downloaded by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    // Decide the entry point: already have the model -> straight to loading; else onboard.
    LaunchedEffect(Unit) {
        phase = if (ModelDownloader(modelFile).isComplete()) Phase.LOADING else Phase.WELCOME
    }

    // Resumable download. Re-runs on resume (paused -> false); cancels cooperatively on pause.
    LaunchedEffect(phase, paused) {
        if (phase == Phase.DOWNLOAD && !paused && !done) {
            try {
                ModelDownloader(modelFile).download(ModelRegistry.MODEL_URL) { p ->
                    downloaded = p.downloaded
                    total = p.total
                }
                done = true
            } catch (c: CancellationException) {
                throw c // a pause/navigation cancelled us; keep the .part file, no error
            } catch (t: Throwable) {
                errorMsg = t.message ?: "network error"
                phase = Phase.ERROR
            }
        }
    }

    // Load the model into the brain off the UI thread; fall back to keyword mode if it fails.
    LaunchedEffect(phase) {
        if (phase == Phase.LOADING) {
            brain = withContext(Dispatchers.IO) {
                try {
                    LiteRtBrain(modelFile.absolutePath, cacheDir)
                } catch (t: Throwable) {
                    null
                }
            }
            phase = Phase.CHAT
        }
    }

    when (phase) {
        Phase.BOOT, Phase.LOADING -> LoadingBrainScreen()
        Phase.WELCOME -> WelcomeScreen(onStart = { phase = Phase.DOWNLOAD })
        Phase.DOWNLOAD -> DownloadScreen(
            downloaded = downloaded,
            total = total,
            paused = paused,
            done = done,
            onPauseResume = { paused = !paused },
            onContinue = { phase = Phase.LOADING },
        )
        Phase.CHAT -> ChatScreen(brain = brain, agent = agent)
        Phase.ERROR -> ErrorScreen(
            message = errorMsg,
            onRetry = {
                errorMsg = ""
                done = false
                paused = false
                phase = Phase.DOWNLOAD
            },
            onSkip = { phase = Phase.CHAT }, // keyword mode, no brain
        )
    }
}
