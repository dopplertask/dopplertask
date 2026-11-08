package com.dopplertask.dopplertask.domain

import java.io.ByteArrayInputStream
import java.net.URLConnection

/**
 * The files of a run, by name: what Read file and connector downloads keep, and what Write file and connector uploads
 * send. A file is kept with the run, so that a step later in the run, or a resumed run, can still use it.
 */
object RunFiles {

    /** A file of the run with the name it is kept by. */
    class RunFile(val name: String, val bytes: ByteArray) {
        val mimeType: String
            get() = mimeTypeOf(name, bytes)
    }

    fun keep(execution: TaskExecution, name: String, bytes: ByteArray) {
        execution.parameters[name] = ExecutionParameter(name, bytes, true)
    }

    fun read(execution: TaskExecution, name: String): ByteArray? = execution.parameters[name]?.takeIf { it.isBinary }?.paramValue

    /** What kind of file it is, by its name or else by its first bytes. */
    fun mimeTypeOf(name: String, bytes: ByteArray): String = URLConnection.guessContentTypeFromName(name)
        ?: URLConnection.guessContentTypeFromStream(ByteArrayInputStream(bytes))
        ?: "application/octet-stream"

    /** What a step gives about a file it kept, for later steps to read. Its type comes from [originalName], such as june.pdf. */
    fun describe(name: String, bytes: ByteArray, originalName: String = name): MutableMap<String, Any> =
        mutableMapOf("file" to name, "size" to bytes.size, "mimeType" to mimeTypeOf(originalName, bytes))

    fun isText(mimeType: String) = mimeType.startsWith("text/") || mimeType in setOf("application/json", "application/xml", "application/csv")
}
