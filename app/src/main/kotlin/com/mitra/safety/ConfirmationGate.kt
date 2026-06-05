package com.mitra.safety

import com.mitra.tools.SideEffect

/**
 * The single source of truth for "must this tool call show a Confirm card first?"
 * Pure function over [SideEffect] so it's trivially unit-testable and not coupled to UI.
 *
 * Policy today: only [SideEffect.None] runs without a confirmation. Anything Reversible
 * or Irreversible shows the action card and waits for the user. A future user setting
 * ("strict / balanced / loose") plugs in here.
 */
object ConfirmationGate {
    fun requiresConfirm(sideEffect: SideEffect?): Boolean = when (sideEffect) {
        SideEffect.None -> false
        null -> true // unknown tool — fail safe
        else -> true
    }
}
