package com.dopplertask.dopplertask.domain

data class ActionResult(
    var statusCode: StatusCode,
    var output: String,
    var errorMsg: String,
    var outputType: OutputType,
    var isBroadcastMessage: Boolean,
    var outputVariables: MutableMap<String, Any>
) {
    constructor(statusCode: StatusCode, output: String, errorMsg: String) : this(
        statusCode,
        output,
        errorMsg,
        OutputType.STRING,
        false,
        mutableMapOf<String, Any>()
    )

    constructor() : this(StatusCode.SUCCESS, "", "")
}