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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mitra.agent.AgentRuntime
import com.mitra.agent.IntentParser
import com.mitra.agent.TurnOnlyContextStore
import com.mitra.automation.ManagerApiBackend
import com.mitra.inference.Brain
import com.mitra.inference.BrainHolder
import com.mitra.inference.BrainResidentService
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
        val brainHolder = (application as MitraApp).brainHolder
        val modelFile = File(applicationContext.getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        setContent {
            MitraTheme {
                AppRoot(
                    modelFile = modelFile,
                    brainHolder = brainHolder,
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
    brainHolder: BrainHolder,
    auditEntries: () -> List<com.mitra.safety.AuditLog.Entry>,
    buildRuntime: (Brain?, (String) -> Unit) -> AgentRuntime,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var phase by remember { mutableStateOf(Phase.BOOT) }
    var brain by remember { mutableStateOf<Brain?>(null) }

    var downloaded by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val modelOnDisk = ModelDownloader(modelFile).isComplete()
        val warmBrain = brainHolder.peek()
        phase =
            when {
                !modelOnDisk -> Phase.WELCOME
                // Process survived (BrainResidentService kept it alive) AND brain is already
                // constructed — skip LoadingBrainScreen entirely; go straight to chat. This is
                // the fast-path that makes re-opening the app feel instant.
                warmBrain != null -> {
                    brain = warmBrain
                    if (Onboarding.isComplete(ctx)) Phase.CHAT else Phase.PERMISSIONS
                }
                else -> Phase.LOADING
            }
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
            // Cover the freshly-downloaded path: MitraApp.onCreate skipped its eager prewarm
            // because the model file didn't exist yet. Now it does, so kick construction off.
            // On launches AFTER the first download completes, prewarm() is a no-op (single-flight).
            brainHolder.prewarm()
            brain = brainHolder.get()
            // Promote the process to foreground-service tier so swiping the app from recents or
            // tight LMK pressure don't unload the ~2.6 GB model. Service idempotent + START_STICKY.
            // Started here (visible activity) to avoid ForegroundServiceStartNotAllowedException
            // on API 31+.
            if (brain != null) {
                BrainResidentService.start(ctx)
                // Silent KV-cache prefill — eat the ~45s system-prompt + tool-descriptions
                // prefill cost HERE on LoadingBrainScreen, so the user's first real chat message
                // returns in ~1-2s instead of 45s. The "hi" turn is consumed off-screen; brain
                // conversation will contain it (subtle context pollution, accepted V1 trade-off
                // — amortizing cold-start latency is worth more than a perfectly empty opening).
                // On warm reopens (process survived), peek() above skips this branch entirely,
                // so this cost is paid once per cold process.
                runCatching { brain!!.chatStream("hi").collect { /* discard */ } }
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
