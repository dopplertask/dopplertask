package com.dopplertask.dopplertask.domain

data class ActionResult(var statusCode: StatusCode, var output: String, var errorMsg: String, var outputType: OutputType, var isBroadcastMessage: Boolean) {
    constructor(statusCode: StatusCode, output: String, errorMsg: String) : this(statusCode, output, errorMsg, OutputType.STRING, false)
    constructor() : this(StatusCode.SUCCESS, "", "")
}