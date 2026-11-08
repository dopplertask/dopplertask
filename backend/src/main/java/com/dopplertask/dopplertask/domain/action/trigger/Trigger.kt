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

    /** Why the trigger cannot start the task, or null when it can. Asked when the task is turned on and when triggers start. */
    open fun problem(): String? = null

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo

            return actionInfo
        }
}

data class TriggerResult(val resultMap: MutableMap<String, String>)
