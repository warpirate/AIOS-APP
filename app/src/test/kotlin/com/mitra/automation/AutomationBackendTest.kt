package com.mitra.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationBackendTest {
    @Test
    fun `tier ordering is ManagerApi RemoteInput Deeplink A11yGesture`() {
        // ordinal order matters — dispatcher picks lowest ordinal (cheapest tier) first
        assertEquals(0, AutomationTier.ManagerApi.ordinal)
        assertEquals(1, AutomationTier.RemoteInput.ordinal)
        assertEquals(2, AutomationTier.Deeplink.ordinal)
        assertEquals(3, AutomationTier.A11yGesture.ordinal)
    }

    @Test
    fun `tool dispatch action carries name and args`() {
        val action = AutomationAction.ToolDispatch("toggle_flashlight", mapOf("on" to true))
        assertEquals("toggle_flashlight", action.name)
        assertEquals(true, action.args["on"])
    }

    @Test
    fun `backend result success and failure are distinguishable`() {
        val ok: BackendResult = BackendResult.Success("done")
        val err: BackendResult = BackendResult.Failure("nope")
        assertTrue(ok is BackendResult.Success)
        assertTrue(err is BackendResult.Failure)
    }
}
