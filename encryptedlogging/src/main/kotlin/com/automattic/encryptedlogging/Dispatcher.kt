package com.automattic.encryptedlogging

import android.util.Log
import com.automattic.encryptedlogging.store.Store
import org.greenrobot.eventbus.EventBus
import org.wordpress.android.fluxc.annotations.action.Action

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

    fun dispatch(action: Action<*>) {
        Log.d(TAG, "Dispatching action: " + action.type.javaClass.simpleName
                + "-" + action.type.toString())
        post(action)
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
