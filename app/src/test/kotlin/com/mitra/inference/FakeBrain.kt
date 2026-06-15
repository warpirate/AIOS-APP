package com.mitra.inference

import com.mitra.agent.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * Scripted brain for AgentRuntime unit tests. Each scripted "leg" is the list of [BrainTurn]
 * emissions you want one `chatStream` / `sendToolResult` call to produce. Legs are consumed in
 * order — first call returns the first leg, second call returns the second leg, and so on. The
 * runtime is single-threaded per turn so the order is deterministic.
 *
 * Helper builders make the scripts readable:
 *   FakeBrain.script(
 *       leg("hello") { tool("toggle_flashlight", mapOf("on" to true)) },
 *       leg("done."),
 *   )
 *
 * If the script is exhausted (runtime called the brain one more time than scripted), the next
 * call returns an empty Flow — the runtime treats that as "brain went quiet" and emits Failed.
 */
class FakeBrain(
    private val legs: MutableList<List<BrainTurn>>,
) : Brain {
    val sentResults: MutableList<Pair<String, Map<String, Any?>>> = mutableListOf()

    override fun chatStream(userText: String): Flow<BrainTurn> = nextLeg()

    override fun sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn> {
        sentResults += toolName to result
        return nextLeg()
    }

    private fun nextLeg(): Flow<BrainTurn> =
        if (legs.isEmpty()) emptyList<BrainTurn>().asFlow() else legs.removeAt(0).asFlow()

    companion object {
        fun script(vararg legs: List<BrainTurn>): FakeBrain = FakeBrain(legs.toMutableList())

        /** Build one leg from a small DSL: leg("text") { tool("foo", mapOf(...)) }. */
        fun leg(text: String, build: LegBuilder.() -> Unit = {}): List<BrainTurn> {
            val b = LegBuilder(text)
            b.build()
            return b.emissions
        }

        class LegBuilder(private val baseText: String) {
            val emissions = mutableListOf<BrainTurn>()

            init {
                if (baseText.isNotEmpty()) emissions += BrainTurn(text = baseText)
            }

            fun tool(name: String, args: Map<String, Any?> = emptyMap()) {
                emissions += BrainTurn(text = baseText, toolCall = ToolCall(name, args))
            }
        }
    }
}
