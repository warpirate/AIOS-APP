package com.mitra.safety

import com.mitra.tools.SideEffect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationGateTest {

    @Test
    fun `None bypasses confirmation`() {
        assertFalse(ConfirmationGate.requiresConfirm(SideEffect.None))
    }

    @Test
    fun `Reversible requires confirmation`() {
        assertTrue(ConfirmationGate.requiresConfirm(SideEffect.Reversible))
    }

    @Test
    fun `Irreversible requires confirmation`() {
        assertTrue(ConfirmationGate.requiresConfirm(SideEffect.Irreversible))
    }

    @Test
    fun `unknown tool fails safe and requires confirmation`() {
        assertTrue(ConfirmationGate.requiresConfirm(null))
    }
}
