package com.mitra.tools

import android.content.Context

/**
 * Switches the screen brightness to adaptive (auto) mode. Sibling of [SetBrightness] — declared
 * as a separate `@Tool` because LiteRT-LM's reflection-based schema builder doesn't reliably
 * support optional / nullable args, so we expose the two intents as two zero-overlap tools:
 *
 *   - `set_brightness(level: Int)` — manual brightness at a specific percentage.
 *   - `set_brightness_auto()` — adaptive brightness on (no level needed).
 *
 * Forwards to [SetBrightness] so all the WRITE_SETTINGS bounce + audit + undo plumbing is
 * inherited for free. The forward call passes `auto = true`; execute respects it.
 *
 * Undo behaviour: captures the current brightness mode + level via [SetBrightness.captureUndo],
 * so toggling auto on → undo restores manual at the prior percentage (and vice versa).
 */
class SetBrightnessAuto(
    context: Context,
) : Tool {
    override val name = "set_brightness_auto"
    override val sideEffect = SideEffect.Reversible

    private val delegate = SetBrightness(context)
    private val autoArgs: Map<String, Any?> = mapOf("auto" to true)

    override fun execute(args: Map<String, Any?>): ToolResult = delegate.execute(autoArgs)

    override fun captureUndo(args: Map<String, Any?>): UndoSpec? = delegate.captureUndo(autoArgs)
}
