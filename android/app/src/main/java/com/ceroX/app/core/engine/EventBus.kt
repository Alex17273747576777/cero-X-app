package com.ceroX.app.core.engine

import java.util.concurrent.ConcurrentHashMap

class EventBus {

    private val subscribers = ConcurrentHashMap<String, MutableList<(Any) -> Unit>>()

    fun subscribe(eventType: String, callback: (Any) -> Unit) {
        subscribers.computeIfAbsent(eventType) { mutableListOf() }.add(callback)
    }

    fun unsubscribe(eventType: String, callback: (Any) -> Unit) {
        subscribers[eventType]?.remove(callback)
    }

    fun publish(eventType: String, data: Any?) {
        val callbacks = subscribers[eventType] ?: return
        for (cb in callbacks) {
            try {
                cb(data as Any)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}