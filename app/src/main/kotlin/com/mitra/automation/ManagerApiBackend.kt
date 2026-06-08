package com.mitra.automation

import com.mitra.tools.Tool
import com.mitra.tools.ToolResult

/**
 * Default V1 backend: dispatches a [AutomationAction.ToolDispatch] to the matching [Tool] by name
 * via direct Android Manager-API calls (whatever the tool implementation does inside execute).
 * No notification listener, no a11y, no intent — those are higher-tier backends added later.
 */
class ManagerApiBackend(private val toolsByName: Map<String, Tool>) : AutomationBackend {
    override val tier = AutomationTier.ManagerApi

    override fun supports(action: AutomationAction): Boolean = action is AutomationAction.ToolDispatch

    override suspend fun execute(action: AutomationAction): BackendResult {
        val td = action as? AutomationAction.ToolDispatch
            ?: return BackendResult.Failure("ManagerApiBackend cannot execute ${action::class.simpleName}")
        val tool = toolsByName[td.name]
            ?: return BackendResult.Failure("no tool registered for ${td.name}")
        return when (val r = tool.execute(td.args)) {
            is ToolResult.Success -> BackendResult.Success(r.message)
            is ToolResult.Failure -> BackendResult.Failure(r.message)
        }
    }
}
