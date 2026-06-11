// app/src/main/kotlin/com/mitra/agent/InvocationSource.kt
package com.mitra.agent

import kotlinx.coroutines.flow.Flow

enum class ScreenOrigin { Foreground, Lockscreen, Background }

/** A single text utterance from a user-facing source (tile / assistant role / power key / wake word). */
data class UserUtterance(
    val text: String,
    val source: String,
    val origin: ScreenOrigin = ScreenOrigin.Foreground,
)

/**
 * Phase 1 will land impls (QuickSettingsTile, AssistantRole, PowerKey). Phase 0 ships the
 * interface only so AgentRuntime callers can be typed against it without behavior change.
 */
interface InvocationSource {
    val id: String

    fun events(): Flow<UserUtterance>
}
