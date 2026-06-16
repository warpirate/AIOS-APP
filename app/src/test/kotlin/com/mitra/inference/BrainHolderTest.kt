package com.mitra.inference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BrainHolderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() { scope.coroutineContext[Job]?.cancel() }

    @Test
    fun `get returns the instance produced by the factory`() = runBlocking {
        val brain: Brain = fakeBrain()
        val holder = BrainHolder(factory = { brain }, scope = scope)
        assertSame(brain, holder.get())
    }

    @Test
    fun `factory is invoked exactly once across many concurrent get calls`() = runBlocking {
        val invocations = AtomicInteger(0)
        val brain: Brain = fakeBrain()
        val holder =
            BrainHolder(
                factory = {
                    invocations.incrementAndGet()
                    Thread.sleep(20)
                    brain
                },
                scope = scope,
            )
        val results = (1..10).map { async { holder.get() } }.awaitAll()
        assertEquals(1, invocations.get())
        results.forEach { assertSame(brain, it) }
    }

    @Test
    fun `failure is sticky - second get does not re-invoke the factory`() = runBlocking {
        val invocations = AtomicInteger(0)
        val holder =
            BrainHolder(
                factory = {
                    invocations.incrementAndGet()
                    null
                },
                scope = scope,
            )
        assertNull(holder.get())
        assertNull(holder.get())
        assertEquals(1, invocations.get())
    }

    @Test
    fun `prewarm starts construction without suspending the caller`() = runBlocking {
        val started = AtomicInteger(0)
        val brain: Brain = fakeBrain()
        val holder =
            BrainHolder(
                factory = {
                    started.incrementAndGet()
                    Thread.sleep(50)
                    brain
                },
                scope = scope,
            )
        holder.prewarm()
        delay(5)
        withTimeout(500) { assertSame(brain, holder.get()) }
        assertEquals(1, started.get())
    }

    private fun fakeBrain(): Brain = FakeBrain.script()
}
