package com.dopplertask.dopplertask.service

interface TriggerListenerService {
    fun startActiveTriggers()
    fun updateTriggers(id: Long)
}