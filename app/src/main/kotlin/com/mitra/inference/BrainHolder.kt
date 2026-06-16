package com.mitra.inference

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-lifetime singleton for the [Brain] instance. Constructed lazily on first [get] (or
 * eagerly via [prewarm] when the caller knows the construction prerequisites are met — e.g.
 * the model file is on disk). Survives Activity recreation; killed only when the OS
 * terminates the process. Failure is sticky for the lifetime of the process — a corrupt
 * model, missing model file, or JNI init failure is not something we retry on a per-Activity
 * basis. The sibling-conversation warmup that this design replaces paid the prefill cost on
 * a throwaway Conversation; the user's first real message paid it again. With construction
 * pinned to the Application lifetime, the real conversation's KV cache survives every
 * Activity recreation, so the cost is paid once per process lifetime.
 *
 * Types in terms of [Brain] (the interface) rather than [LiteRtBrain] so unit tests can
 * substitute the existing `FakeBrain` test double — the JNI-bound `LiteRtBrain` itself
 * doesn't load on the JVM.
 */
class BrainHolder(
    private val factory: () -> Brain?,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    @Volatile private var deferred: CompletableDeferred<Brain?>? = null

    /** Eagerly start construction. Safe to call from MitraApp.onCreate or AppRoot's LOADING
     *  branch to overlap construction with the Activity boot path. Subsequent calls are
     *  no-ops because the [CompletableDeferred] is single-flight. */
    fun prewarm() {
        scope.launch { ensureStarted() }
    }

    /** Returns the singleton brain, or null on construction failure. Suspends if construction
     *  is in flight. */
    suspend fun get(): Brain? = ensureStarted().await()

    private suspend fun ensureStarted(): CompletableDeferred<Brain?> {
        deferred?.let { return it }
        return mutex.withLock {
            deferred?.let { return@withLock it }
            val d = CompletableDeferred<Brain?>()
            deferred = d
            scope.launch(Dispatchers.IO) {
                d.complete(factory())
            }
            d
        }
    }
}
