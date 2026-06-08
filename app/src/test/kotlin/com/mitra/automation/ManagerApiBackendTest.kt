package com.mitra.automation

import com.mitra.tools.SideEffect
import com.mitra.tools.Tool
import com.mitra.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTool(
    override val name: String,
    private val result: ToolResult,
) : Tool {
    override val sideEffect = SideEffect.None
    var lastArgs: Map<String, Any?>? = null
    override fun execute(args: Map<String, Any?>): ToolResult {
        lastArgs = args
        return result
    }
}

class ManagerApiBackendTest {
    @Test
    fun `supports tool dispatch only`() {
        val backend = ManagerApiBackend(toolsByName = emptyMap())
        assertTrue(backend.supports(AutomationAction.ToolDispatch("foo", emptyMap())))
    }

    @Test
    fun `execute dispatches to the tool by name and forwards args`() = runBlocking {
        val tool = FakeTool("toggle_flashlight", ToolResult.Success("on"))
        val backend = ManagerApiBackend(mapOf(tool.name to tool))
        val r = backend.execute(AutomationAction.ToolDispatch("toggle_flashlight", mapOf("on" to true)))
        assertTrue(r is BackendResult.Success)
        assertEquals("on", (r as BackendResult.Success).message)
        assertEquals(true, tool.lastArgs?.get("on"))
    }

    @Test
    fun `unknown tool returns failure`() = runBlocking {
        val backend = ManagerApiBackend(toolsByName = emptyMap())
        val r = backend.execute(AutomationAction.ToolDispatch("nope", emptyMap()))
        assertTrue(r is BackendResult.Failure)
    }

    @Test
    fun `tool failure surfaces as backend failure`() = runBlocking {
        val tool = FakeTool("x", ToolResult.Failure("boom"))
        val backend = ManagerApiBackend(mapOf("x" to tool))
        val r = backend.execute(AutomationAction.ToolDispatch("x", emptyMap()))
        assertTrue(r is BackendResult.Failure)
        assertEquals("boom", (r as BackendResult.Failure).message)
    }

    @Test
    fun `tier is ManagerApi`() {
        assertEquals(AutomationTier.ManagerApi, ManagerApiBackend(emptyMap()).tier)
    }
}
