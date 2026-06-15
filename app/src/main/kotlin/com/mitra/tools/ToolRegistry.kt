package com.mitra.tools

import android.content.Context

/**
 * The registered tool set. Add new tools here (and declare them to the model in
 * LiteRtBrain.PhoneTools so it knows they exist). AgentLoop dispatches a model-emitted
 * ToolCall to the matching Tool by name.
 */
object ToolRegistry {
    fun all(context: Context): List<Tool> =
        listOf(
            ToggleFlashlight(context),
            SetAlarm(context),
            StartTimer(context),
            OpenUrl(context),
            OpenApp(context),
            OpenSettings(context),
            QueryContacts(context),
            MakeCall(context),
            SendSms(context),
            SetMediaVolume(context),
            SetBrightness(context),
            SetDnd(context),
            SetRingerMode(context),
            SetAutoRotate(context),
            SetScreenTimeout(context),
            SetBluetooth(context),
        )
}
