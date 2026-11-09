package com.dopplertask.dopplertask.domain

data class ActionResult(
    var statusCode: StatusCode,
    var output: String,
    var errorMsg: String,
    var outputType: OutputType,
    var isBroadcastMessage: Boolean,
    var outputVariables: MutableMap<String, Any>
) {
    /** Set by a step that waits for an answer: the run stops after it, and continues when its key is called once. */
    var waitKey: String? = null

    constructor(statusCode: StatusCode, output: String, errorMsg: String) : this(
        statusCode,
        output,
        errorMsg,
        OutputType.STRING,
        false,
        mutableMapOf<String, Any>()
    )

    constructor() : this(StatusCode.SUCCESS, "", "")

    companion object {
        fun failure(message: String) = ActionResult(StatusCode.FAILURE, "", message)
    }
}