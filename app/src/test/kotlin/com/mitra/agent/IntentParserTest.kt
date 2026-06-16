package com.mitra.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentParserTest {
    private val parser = IntentParser()

    @Test
    fun `flashlight on`() {
        val c = parser.route("turn on the flashlight")
        assertEquals("toggle_flashlight", c?.name)
        assertEquals(true, c?.args?.get("on"))
    }

    @Test
    fun `flashlight off`() {
        val c = parser.route("turn off the torch")
        assertEquals("toggle_flashlight", c?.name)
        assertEquals(false, c?.args?.get("on"))
    }

    @Test
    fun `alarm with time`() {
        val c = parser.route("set an alarm for 7:30 am")
        assertEquals("set_alarm", c?.name)
        assertEquals(7, c?.args?.get("hour"))
        assertEquals(30, c?.args?.get("minute"))
    }

    @Test
    fun `pm alarm converts to 24h`() {
        val c = parser.route("wake me at 6 pm")
        assertEquals("set_alarm", c?.name)
        assertEquals(18, c?.args?.get("hour"))
    }

    @Test
    fun `timer in minutes to seconds`() {
        val c = parser.route("start a 5 minute timer")
        assertEquals("start_timer", c?.name)
        assertEquals(300, c?.args?.get("seconds"))
    }

    @Test
    fun `volume percent`() {
        val c = parser.route("set volume to 40%")
        assertEquals("set_media_volume", c?.name)
        assertEquals(40, c?.args?.get("level"))
    }

    @Test
    fun `open url`() {
        val c = parser.route("open youtube.com")
        assertEquals("open_url", c?.name)
        assertEquals("youtube.com", c?.args?.get("url"))
    }

    @Test
    fun `open app by name`() {
        val c = parser.route("open spotify")
        assertEquals("open_app", c?.name)
        assertEquals("spotify", c?.args?.get("name"))
    }

    @Test
    fun `launch app with trailing word app`() {
        val c = parser.route("launch the calculator app")
        assertEquals("open_app", c?.name)
        assertEquals("calculator", c?.args?.get("name"))
    }

    @Test
    fun `open URL beats open app when the target has a dot`() {
        val c = parser.route("open example.com")
        assertEquals("open_url", c?.name)
    }

    @Test
    fun `brightness percent`() {
        val c = parser.route("set brightness to 40%")
        assertEquals("set_brightness", c?.name)
        assertEquals(40, c?.args?.get("level"))
    }

    @Test
    fun `dim the screen sets a low brightness`() {
        val c = parser.route("dim the screen")
        assertEquals("set_brightness", c?.name)
        assertEquals(10, c?.args?.get("level"))
    }

    @Test
    fun `brightness auto routes to set_brightness_auto`() {
        val c = parser.route("brightness auto")
        assertEquals("set_brightness_auto", c?.name)
        assertEquals(emptyMap<String, Any?>(), c?.args)
    }

    @Test
    fun `set brightness to auto routes to set_brightness_auto, not numeric set_brightness`() {
        val c = parser.route("set brightness to auto")
        assertEquals("set_brightness_auto", c?.name)
    }

    @Test
    fun `adaptive brightness routes to set_brightness_auto`() {
        val c = parser.route("adaptive brightness on")
        assertEquals("set_brightness_auto", c?.name)
    }

    @Test
    fun `volume not confused with brightness`() {
        val c = parser.route("set volume to 30")
        assertEquals("set_media_volume", c?.name)
    }

    @Test
    fun `start a 5 minute timer is a timer not an open_app`() {
        val c = parser.route("start a 5 minute timer")
        assertEquals("start_timer", c?.name)
        assertEquals(300, c?.args?.get("seconds"))
    }

    @Test
    fun `chit-chat falls through to the LLM`() {
        assertNull(parser.route("how are you today"))
    }

    @Test
    fun `bluetooth opens the settings panel not the app`() {
        val c = parser.route("Bluetooth?")
        assertEquals("open_settings", c?.name)
        assertEquals("bluetooth", c?.args?.get("panel"))
    }

    @Test
    fun `wifi opens the settings panel`() {
        val c = parser.route("wifi off")
        assertEquals("open_settings", c?.name)
        assertEquals("wifi", c?.args?.get("panel"))
    }

    @Test
    fun `do not disturb routes to set_dnd`() {
        val c = parser.route("turn on do not disturb")
        assertEquals("set_dnd", c?.name)
        assertEquals(true, c?.args?.get("on"))
    }

    @Test
    fun `airplane mode opens airplane panel`() {
        val c = parser.route("airplane mode")
        assertEquals("open_settings", c?.name)
        assertEquals("airplane", c?.args?.get("panel"))
    }

    @Test
    fun `brightness still wins over display panel`() {
        val c = parser.route("set brightness to 30")
        assertEquals("set_brightness", c?.name)
    }

    @Test
    fun `flashlight wins over panel even if torch mentioned`() {
        val c = parser.route("turn off the flashlight")
        assertEquals("toggle_flashlight", c?.name)
    }

    @Test
    fun `whats moms number routes to query_contacts`() {
        val c = parser.route("what's mom's number")
        assertEquals("query_contacts", c?.name)
        assertEquals("mom", c?.args?.get("name"))
    }

    @Test
    fun `find priya routes to query_contacts`() {
        val c = parser.route("find priya")
        assertEquals("query_contacts", c?.name)
        assertEquals("priya", c?.args?.get("name"))
    }

    @Test
    fun `contact raj routes to query_contacts`() {
        val c = parser.route("contact raj")
        assertEquals("query_contacts", c?.name)
        assertEquals("raj", c?.args?.get("name"))
    }
}
