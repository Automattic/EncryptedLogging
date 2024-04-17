package com.automattic.encryptedlogging.store

import com.automattic.encryptedlogging.Dispatcher
import org.wordpress.android.fluxc.annotations.action.Action

internal abstract class Store internal constructor(private val mDispatcher: Dispatcher) {
    init {
        mDispatcher.register(this)
    }

    /**
     * onAction should [org.greenrobot.eventbus.Subscribe] with ASYNC [org.greenrobot.eventbus.ThreadMode].
     */
    abstract fun onAction(action: Action<*>)
    abstract fun onRegister()
    protected fun emitChange(onChangedEvent: OnChanged<*>) {
        mDispatcher.emitChange(onChangedEvent)
    }
}
