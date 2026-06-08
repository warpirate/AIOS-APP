package com.mitra.agent

import com.mitra.tools.SideEffect

/** What the [Planner] returns: an ordered list of tool calls to execute, plus model self-report fields. */
data class Plan(
    val steps: List<PlannedStep>,
    val rationale: String?,
    val confidence: Float,
)

data class PlannedStep(
    val toolName: String,
    val args: Map<String, Any?>,
    val sideEffect: SideEffect,
    val dependsOn: List<Int> = emptyList(),
)
