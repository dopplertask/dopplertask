package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.action.Action
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Transient

@MappedSuperclass
abstract class Trigger : Action() {

    @Column
    var triggerSuffix = ""

    @JsonIgnore
    @Transient
    var parameters: Map<String, String>? = null

    abstract fun trigger(): TriggerResult

    /** Called when the task stops, so that a trigger can let go of what it holds, such as a subscription. */
    open fun stop() {}

    /** Why the trigger cannot start the task, or null when it can. Asked when the task is turned on and when triggers start. */
    open fun problem(): String? = null

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo

            return actionInfo
        }
}

data class TriggerResult(val resultMap: MutableMap<String, String>)
