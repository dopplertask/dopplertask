package com.dopplertask.dopplertask.domain.action.trigger

import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.Column
import javax.persistence.MappedSuperclass
import javax.persistence.Transient

@MappedSuperclass
abstract class Trigger : Action() {
    @Column
    var path = ""

    @Column
    var triggerSuffix = ""

    @JsonIgnore
    @Transient
    var parameters: Map<String, String>? = null

    abstract fun trigger(): TriggerResult

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("path", "Path", PropertyInformationType.STRING))
            return actionInfo
        }
}

data class TriggerResult(val resultMap : MutableMap<String, String>)
