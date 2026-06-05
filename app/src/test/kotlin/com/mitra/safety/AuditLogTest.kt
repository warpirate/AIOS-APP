package com.mitra.safety

import com.mitra.tools.SideEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogTest {

    @Test
    fun `records tool name side-effect class and outcome`() {
        val log = AuditLog()
        log.record("toggle_flashlight", SideEffect.None, ok = true)
        val e = log.entries().single()
        assertEquals("toggle_flashlight", e.toolName)
        assertEquals(SideEffect.None, e.sideEffect)
        assertTrue(e.ok)
    }

    @Test
    fun `unknown tool records null side-effect and failure`() {
        val log = AuditLog()
        log.record("does_not_exist", sideEffect = null, ok = false)
        val e = log.entries().single()
        assertEquals("does_not_exist", e.toolName)
        assertEquals(null, e.sideEffect)
        assertFalse(e.ok)
    }

    @Test
    fun `ring buffer drops oldest at capacity`() {
        val log = AuditLog(capacity = 3)
        repeat(5) { i -> log.record("tool_$i", SideEffect.None, ok = true) }
        val names = log.entries().map { it.toolName }
        assertEquals(listOf("tool_2", "tool_3", "tool_4"), names)
    }

    @Test
    fun `clear empties the buffer`() {
        val log = AuditLog()
        log.record("toggle_flashlight", SideEffect.None, ok = true)
        log.clear()
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun `Entry data class only has whitelisted fields`() {
        // Privacy invariant: no field on an Entry can ever carry user content. This test fails
        // the moment someone adds a String field that could leak (args, message body, contact name).
        val fields = AuditLog.Entry::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(setOf("toolName", "sideEffect", "ok", "timestampMs"), fields)
    }
}
