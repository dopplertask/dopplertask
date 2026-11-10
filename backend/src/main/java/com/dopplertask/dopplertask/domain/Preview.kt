package com.dopplertask.dopplertask.domain

import java.util.concurrent.Callable

/**
 * A run that shows what its steps would send, and sends none of it. Steps that only read run as usual, so that the
 * steps after them work on real values. Steps are named by the id of their first port, as in [TaskExecutionStep].
 */
class Preview {
    /** Steps the person chose to send for real. */
    var sentForReal: Set<String> = setOf()

    /** Steps that fail here, so that the run takes the path the task has for a failure. */
    var madeToFail: Set<String> = setOf()

    /** What a held step gives the steps after it, in place of the answer it never got. */
    var exampleAnswers: Map<String, String> = mapOf()

    companion object {
        private const val LONGEST_SHOWN_BODY = 4000
        private val READING_METHODS = setOf("GET", "HEAD", "OPTIONS")

        // What the step on this thread tried to send. There is none when the thread sends for real.
        private val heldWrites = ThreadLocal<MutableList<String>?>()

        @JvmStatic
        fun isHolding(): Boolean = heldWrites.get() != null

        /** Stops a web request that would change something, and keeps what it would have sent. */
        @JvmStatic
        fun holdRequest(method: String?, address: String, body: ByteArray) {
            val writes = heldWrites.get()
            if (writes == null || method in READING_METHODS) return
            val shownBody = if (body.size > LONGEST_SHOWN_BODY) "(${body.size} bytes)" else String(body, Charsets.UTF_8)
            writes.add("$method $address" + if (shownBody.isBlank()) "" else "\n$shownBody")
            throw HeldInPreview()
        }

        /**
         * Runs a step with its writes held. A step that tried to send gives what it would have sent and no result of
         * its own, whatever it did with the answer it never got.
         */
        @JvmStatic
        fun run(step: Callable<ActionResult>): ActionResult {
            val writes = mutableListOf<String>()
            // A step of a task that another step runs is held inside the hold of that step.
            val outerWrites = heldWrites.get()
            heldWrites.set(writes)
            val result = try {
                step.call()
            } catch (held: HeldInPreview) {
                ActionResult()
            } finally {
                heldWrites.set(outerWrites)
            }
            return if (writes.isEmpty()) result else notSent(writes.joinToString("\n\n"))
        }

        /** Signing in changes nothing a person would see, and the reads after it need it. */
        @JvmStatic
        fun <T> sendingForReal(request: () -> T): T {
            val writes = heldWrites.get()
            heldWrites.remove()
            try {
                return request()
            } finally {
                if (writes != null) heldWrites.set(writes)
            }
        }

        @JvmStatic
        fun notSent(intended: String): ActionResult = ActionResult().apply { held = intended }
    }
}

/** Ends a step where it would send something that a preview holds. */
class HeldInPreview : RuntimeException("This was not sent, since the run is a preview.")
