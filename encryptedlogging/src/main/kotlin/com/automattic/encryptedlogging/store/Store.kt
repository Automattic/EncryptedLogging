package com.automattic.encryptedlogging.store

import com.automattic.encryptedlogging.Dispatcher

internal abstract class Store internal constructor(private val mDispatcher: Dispatcher) {
    init {
        mDispatcher.register(this)
    }

    /**
     * onAction should [org.greenrobot.eventbus.Subscribe] with ASYNC [org.greenrobot.eventbus.ThreadMode].
     */
    abstract fun onAction()
    abstract fun onRegister()
    protected fun emitChange(onChangedEvent: OnChanged<*>) {
        mDispatcher.emitChange(onChangedEvent)
    }
}
