package com.mitra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mitra.agent.AgentRuntime
import com.mitra.agent.IntentParser
import com.mitra.agent.TurnOnlyContextStore
import com.mitra.automation.ManagerApiBackend
import com.mitra.inference.LiteRtBrain
import com.mitra.inference.ModelDownloader
import com.mitra.inference.ModelRegistry
import com.mitra.permissions.Onboarding
import com.mitra.prefs.ConfirmationMode
import com.mitra.prefs.UserPrefs
import com.mitra.safety.AuditLog
import com.mitra.tools.ToolRegistry
import com.mitra.ui.AuditHistoryScreen
import com.mitra.ui.ChatScreen
import com.mitra.ui.DownloadScreen
import com.mitra.ui.ErrorScreen
import com.mitra.ui.LoadingBrainScreen
import com.mitra.ui.PermissionsEntryMode
import com.mitra.ui.PermissionsScreen
import com.mitra.ui.SettingsScreen
import com.mitra.ui.WelcomeScreen
import com.mitra.ui.theme.MitraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Phase { BOOT, WELCOME, DOWNLOAD, LOADING, PERMISSIONS, CHAT, SETTINGS, PERMISSIONS_REVIEW, ACTIVITY, ERROR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tools = ToolRegistry.all(applicationContext)
        val toolsByName = tools.associateBy { it.name }
        val sideEffectOf: (String) -> com.mitra.tools.SideEffect = { name ->
            toolsByName[name]?.sideEffect ?: com.mitra.tools.SideEffect.Reversible
        }
        val backend = ManagerApiBackend(toolsByName)
        val audit = AuditLog()
        val context = TurnOnlyContextStore()
        // Read the user's confirmation aggressiveness per-step so a mid-conversation Settings
        // change takes effect on the next dispatch (vs. requiring an app restart).
        val requiresGate: (com.mitra.tools.SideEffect) -> Boolean = { side ->
            when (UserPrefs.confirmationMode(applicationContext)) {
                ConfirmationMode.STRICT -> side != com.mitra.tools.SideEffect.None
                ConfirmationMode.BALANCED -> side == com.mitra.tools.SideEffect.Irreversible
            }
        }
        // The runtime is constructed below once the brain is (or isn't) loaded — see AppRoot.
        val modelFile = File(applicationContext.getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        val cacheDir = applicationContext.cacheDir.path
        setContent {
            MitraTheme {
                AppRoot(
                    modelFile = modelFile,
                    cacheDir = cacheDir,
                    sideEffectOf = sideEffectOf,
                    auditEntries = { audit.entries() },
                    buildRuntime = { brain, _ ->
                        // ChatScreen reads streaming text from RuntimeEvent.Speaking; the legacy
                        // onChunk callback is intentionally ignored. brain == null falls through to
                        // the IntentParser-only path inside AgentRuntime.
                        AgentRuntime(
                            brain = brain,
                            parser = IntentParser(),
                            sideEffectOf = sideEffectOf,
                            backends = listOf(backend),
                            context = context,
                            audit = audit,
                            requiresGate = requiresGate,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    modelFile: File,
    cacheDir: String,
    sideEffectOf: (String) -> com.mitra.tools.SideEffect,
    auditEntries: () -> List<com.mitra.safety.AuditLog.Entry>,
    buildRuntime: (LiteRtBrain?, (String) -> Unit) -> AgentRuntime,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val warmupScope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.BOOT) }
    var brain by remember { mutableStateOf<LiteRtBrain?>(null) }

    var downloaded by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        phase = if (ModelDownloader(modelFile).isComplete()) Phase.LOADING else Phase.WELCOME
    }

    LaunchedEffect(phase, paused) {
        if (phase == Phase.DOWNLOAD && !paused && !done) {
            try {
                ModelDownloader(modelFile).download(ModelRegistry.MODEL_URL) { p ->
                    downloaded = p.downloaded
                    total = p.total
                }
                done = true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                errorMsg = t.message ?: "network error"
                phase = Phase.ERROR
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase == Phase.LOADING) {
            brain =
                withContext(Dispatchers.IO) {
                    try {
                        LiteRtBrain(modelFile.absolutePath, cacheDir)
                    } catch (t: Throwable) {
                        null
                    }
                }
            // Silent background warmup: pages model into RAM, compiles kernels, primes CPU caches.
            // Runs while the user is on Permissions or Chat empty state. By the time they send their
            // first message, the engine is hot. No loading-screen extension, no user-facing message.
            brain?.let { b ->
                warmupScope.launch(Dispatchers.IO) {
                    runCatching { b.warmup() }
                }
            }
            phase = if (Onboarding.isComplete(ctx)) Phase.CHAT else Phase.PERMISSIONS
        }
    }

    when (phase) {
        Phase.BOOT, Phase.LOADING -> LoadingBrainScreen()
        Phase.WELCOME -> WelcomeScreen(onStart = { phase = Phase.DOWNLOAD })
        Phase.DOWNLOAD ->
            DownloadScreen(
                downloaded = downloaded,
                total = total,
                paused = paused,
                done = done,
                onPauseResume = { paused = !paused },
                onContinue = { phase = Phase.LOADING },
            )
        Phase.PERMISSIONS ->
            PermissionsScreen(
                onContinue = {
                    Onboarding.markComplete(ctx)
                    phase = Phase.CHAT
                },
            )
        // Keep ChatScreen mounted across Settings / PermissionsReview so chat history, in-flight
        // brain streams, and the AgentRuntime coroutine scope survive nav. Earlier versions swapped
        // ChatScreen out of composition when moving to Settings, which (a) wiped the chat list
        // (private remember-scoped state) and (b) cancelled any in-flight conversation.sendMessageAsync
        // mid-stream — LiteRT-LM did not always recover, surfacing as LiteRtLmJniException on the
        // next turn. Settings / PermissionsReview now render as a full-bleed overlay on top.
        Phase.CHAT, Phase.SETTINGS, Phase.PERMISSIONS_REVIEW, Phase.ACTIVITY ->
            Box(modifier = Modifier.fillMaxSize()) {
                ChatScreen(
                    brainReady = brain != null,
                    isWarmingUp = { brain?.warmupComplete == false },
                    buildRuntime = { onChunk -> buildRuntime(brain, onChunk) },
                    onOpenSettings = { phase = Phase.SETTINGS },
                )
                when (phase) {
                    Phase.SETTINGS ->
                        SettingsScreen(
                            onBack = { phase = Phase.CHAT },
                            onViewPermissions = { phase = Phase.PERMISSIONS_REVIEW },
                            onViewActivity = { phase = Phase.ACTIVITY },
                            activityCount = auditEntries().size,
                        )
                    Phase.PERMISSIONS_REVIEW ->
                        PermissionsScreen(
                            mode = PermissionsEntryMode.Review(onBack = { phase = Phase.SETTINGS }),
                        )
                    Phase.ACTIVITY ->
                        AuditHistoryScreen(
                            entries = auditEntries,
                            onBack = { phase = Phase.SETTINGS },
                        )
                    else -> Unit
                }
            }
        Phase.ERROR ->
            ErrorScreen(
                message = errorMsg,
                onRetry = {
                    errorMsg = ""
                    done = false
                    paused = false
                    phase = Phase.DOWNLOAD
                },
                onSkip = { phase = Phase.PERMISSIONS },
            )
    }
}
