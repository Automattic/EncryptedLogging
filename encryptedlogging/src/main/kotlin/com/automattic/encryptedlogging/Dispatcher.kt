package com.automattic.encryptedlogging

import android.util.Log
import com.automattic.encryptedlogging.store.Store
import org.greenrobot.eventbus.EventBus

internal class Dispatcher {
    private val mBus: EventBus = EventBus.builder()
        .logNoSubscriberMessages(true)
        .sendNoSubscriberEvent(true)
        .throwSubscriberException(true)
        .build()

    fun register(`object`: Any?) {
        mBus.register(`object`)
        if (`object` is Store) {
            `object`.onRegister()
        }
    }

    fun unregister(`object`: Any?) {
        mBus.unregister(`object`)
    }

    fun dispatch() {
        Log.d(TAG, "Dispatching")
        post(TODO())
    }

    fun emitChange(changeEvent: Any?) {
        mBus.post(changeEvent)
    }

    private fun post(event: Any) {
        mBus.post(event)
    }

    companion object {
        private val TAG = Dispatcher::class.java.simpleName
    }
}
