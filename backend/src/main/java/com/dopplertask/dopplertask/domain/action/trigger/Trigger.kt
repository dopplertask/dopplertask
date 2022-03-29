package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.action.Action
import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.Column
import javax.persistence.MappedSuperclass
import javax.persistence.Transient

@MappedSuperclass
abstract class Trigger : Action() {

    @Column
    var triggerSuffix = ""

    @JsonIgnore
    @Transient
    var parameters: Map<String, String>? = null

    abstract fun trigger(): TriggerResult

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo

            return actionInfo
        }
}

data class TriggerResult(val resultMap: MutableMap<String, String>)
