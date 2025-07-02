package com.automattic.encryptedlogging.store

internal open class OnChanged<T : OnChangedError> {
    var error: T? = null
    fun isError(): Boolean {
        return error != null
    }
}
