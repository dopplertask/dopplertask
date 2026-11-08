package com.dopplertask.dopplertask.service

import java.util.concurrent.CompletableFuture

/**
 * The answer a waiting webhook caller gets. Whichever comes first gives it: the trigger at once, a Reply to webhook step,
 * or the end of the run. Later replies are left out, since a caller can only be answered once.
 */
class WebhookReply(val mode: String) {

    class Answer(val status: Int, val headers: Map<String, String>, val body: String?)

    private val answer = CompletableFuture<Answer>()

    /** Gives the answer, and says whether it was the first one. */
    fun give(status: Int, headers: Map<String, String>, body: String?): Boolean = answer.complete(Answer(status, headers, body))

    /** What the latest step gave, for a webhook that answers with it when the run ends. */
    @Volatile
    var lastOutput: String? = null

    val isGiven: Boolean
        get() = answer.isDone

    fun answered(): CompletableFuture<Answer> = answer

    companion object {
        /** The run log, as webhooks have always answered. */
        const val RUN_LOG = "runLog"
        const val AT_ONCE = "immediately"
        const val LAST_STEP = "lastStep"
        const val REPLY_STEP = "replyStep"

        /** How each way of answering reads in the editor. */
        val LABELS = linkedMapOf(
            RUN_LOG to "The run log, when the task ends",
            AT_ONCE to "At once, while the task runs on",
            LAST_STEP to "What the last step gave, when the task ends",
            REPLY_STEP to "What a Reply to webhook step sends"
        )
    }
}
