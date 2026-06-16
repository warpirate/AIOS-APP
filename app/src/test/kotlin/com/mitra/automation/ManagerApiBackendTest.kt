package com.mitra.automation

import com.mitra.tools.SideEffect
import com.mitra.tools.Tool
import com.mitra.tools.ToolResult
import com.mitra.tools.UndoSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTool(
    override val name: String,
    private val result: ToolResult,
    private val undo: UndoSpec? = null,
) : Tool {
    override val sideEffect = SideEffect.None
    var lastArgs: Map<String, Any?>? = null
    var captureCalls = 0

    override fun execute(args: Map<String, Any?>): ToolResult {
        lastArgs = args
        return result
    }

    override fun captureUndo(args: Map<String, Any?>): UndoSpec? {
        captureCalls++
        return undo
    }
}

class ManagerApiBackendTest {
    @Test
    fun `supports tool dispatch only`() {
        val backend = ManagerApiBackend(toolsByName = emptyMap())
        assertTrue(backend.supports(AutomationAction.ToolDispatch("foo", emptyMap())))
    }

    @Test
    fun `execute dispatches to the tool by name and forwards args`() =
        runBlocking {
            val tool = FakeTool("toggle_flashlight", ToolResult.Success("on"))
            val backend = ManagerApiBackend(mapOf(tool.name to tool))
            val r = backend.execute(AutomationAction.ToolDispatch("toggle_flashlight", mapOf("on" to true)))
            assertTrue(r is BackendResult.Success)
            assertEquals("on", (r as BackendResult.Success).message)
            assertEquals(true, tool.lastArgs?.get("on"))
        }

    @Test
    fun `unknown tool returns failure`() =
        runBlocking {
            val backend = ManagerApiBackend(toolsByName = emptyMap())
            val r = backend.execute(AutomationAction.ToolDispatch("nope", emptyMap()))
            assertTrue(r is BackendResult.Failure)
        }

    @Test
    fun `tool failure surfaces as backend failure`() =
        runBlocking {
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

    @Test
    fun `captured undo propagates to BackendResult Success`() =
        runBlocking {
            val spec = UndoSpec("set_brightness", mapOf("level" to 80))
            val tool = FakeTool("set_brightness", ToolResult.Success("Brightness set to 30%"), undo = spec)
            val backend = ManagerApiBackend(mapOf("set_brightness" to tool))
            val r = backend.execute(AutomationAction.ToolDispatch("set_brightness", mapOf("level" to 30)))
            assertTrue(r is BackendResult.Success)
            assertEquals(spec, (r as BackendResult.Success).undo)
            assertEquals(1, tool.captureCalls)
        }

    @Test
    fun `undo is dropped when execute fails (never offer a misleading Undo)`() =
        runBlocking {
            val spec = UndoSpec("set_brightness", mapOf("level" to 80))
            val tool = FakeTool("set_brightness", ToolResult.Failure("boom"), undo = spec)
            val backend = ManagerApiBackend(mapOf("set_brightness" to tool))
            val r = backend.execute(AutomationAction.ToolDispatch("set_brightness", mapOf("level" to 30)))
            assertTrue(r is BackendResult.Failure)
            // captureUndo still ran (we couldn't know execute would fail), but the inverse never
            // reaches the UI because Failure has no undo slot.
            assertEquals(1, tool.captureCalls)
        }

    @Test
    fun `null undo from a tool stays null in BackendResult`() =
        runBlocking {
            val tool = FakeTool("toggle_flashlight", ToolResult.Success("on"), undo = null)
            val backend = ManagerApiBackend(mapOf("toggle_flashlight" to tool))
            val r = backend.execute(AutomationAction.ToolDispatch("toggle_flashlight", mapOf("on" to true)))
            assertTrue(r is BackendResult.Success)
            assertNull((r as BackendResult.Success).undo)
        }
}
