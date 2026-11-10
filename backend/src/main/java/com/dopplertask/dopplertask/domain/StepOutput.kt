package com.dopplertask.dopplertask.domain

import com.dopplertask.dopplertask.domain.action.common.LoopAction

/** What a step gave, for a later step to read by the name of the step, such as \$steps["Get order"].data.customer.email. */
class StepOutput(val output: String, val outputVariables: Map<String, Any?>) {
    /** The output as fields when it is JSON, and as the same text when it is not. */
    val data: Any? by lazy { LoopAction.valueOf(output) }
}
