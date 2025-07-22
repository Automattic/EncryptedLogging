package com.automattic.encryptedlogging.store

public open class OnChanged<T : OnChangedError> {
    public var error: T? = null
    public fun isError(): Boolean {
        return error != null
    }
}
