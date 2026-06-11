// app/src/test/kotlin/com/mitra/agent/TurnOnlyContextStoreTest.kt
package com.mitra.agent

import com.mitra.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnOnlyContextStoreTest {
    @Test
    fun `turn is empty before beginTurn`() {
        val s = TurnOnlyContextStore()
        assertNull(s.turn())
    }

    @Test
    fun `beginTurn exposes the utterance via turn`() =
        runBlocking {
            val s = TurnOnlyContextStore()
            val u = UserUtterance(text = "open whatsapp", source = "qs-tile")
            s.beginTurn(u)
            assertEquals("open whatsapp", s.turn()?.utterance?.text)
        }

    @Test
    fun `endTurn clears the turn context`() =
        runBlocking {
            val s = TurnOnlyContextStore()
            s.beginTurn(UserUtterance("hi", "qs-tile"))
            s.endTurn()
            assertNull(s.turn())
        }

    @Test
    fun `lastToolResult is null at turn start`() =
        runBlocking {
            val s = TurnOnlyContextStore()
            s.beginTurn(UserUtterance("hi", "qs-tile"))
            assertNull(s.turn()?.lastToolResult)
        }

    @Test
    fun `recordToolResult attaches to current turn`() =
        runBlocking {
            val s = TurnOnlyContextStore()
            s.beginTurn(UserUtterance("hi", "qs-tile"))
            s.recordToolResult(ToolResult.Success("ok"))
            val r = s.turn()?.lastToolResult
            assertEquals("ok", (r as? ToolResult.Success)?.message)
        }
}
