package com.mitra.automation

/**
 * Tiers in priority order: dispatcher picks the lowest ordinal that supports the action.
 * ManagerApi = direct Android Manager (Camera/Bluetooth/AudioManager). RemoteInput = notification
 * inline reply (no UI). Deeplink = intent / ACTION_SEND. A11yGesture = AccessibilityService text
 * injection or gesture (last resort, slow + brittle).
 */
enum class AutomationTier { ManagerApi, RemoteInput, Deeplink, A11yGesture }

/** What a backend is asked to do. Phase 0 has one shape; later tiers add new sealed cases. */
sealed interface AutomationAction {
    data class ToolDispatch(
        val name: String,
        val args: Map<String, Any?>,
    ) : AutomationAction
    // Future:
    // data class ReplyToNotification(val pkg: String, val text: String) : AutomationAction
    // data class OpenDeeplink(val uri: String) : AutomationAction
    // data class A11yGesture(...) : AutomationAction
}

sealed interface BackendResult {
    data class Success(
        val message: String,
        /** Optional inverse action captured by the dispatcher pre-execute. Non-null only when the
         *  underlying tool implements `Tool.captureUndo` AND the forward execute succeeded. UI
         *  uses this to offer a time-limited Undo affordance on a `Reversible` action card. */
        val undo: com.mitra.tools.UndoSpec? = null,
    ) : BackendResult

    data class Failure(
        val message: String,
    ) : BackendResult
}

interface AutomationBackend {
    val tier: AutomationTier

    fun supports(action: AutomationAction): Boolean

    suspend fun execute(action: AutomationAction): BackendResult
}
