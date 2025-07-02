package com.automattic.encryptedlogging.store

internal sealed class UploadEncryptedLogError : OnChangedError {
    class Unknown(
        val statusCode: Int? = null,
        val message: String? = null
    ) : UploadEncryptedLogError()

    object InvalidRequest : UploadEncryptedLogError()
    object TooManyRequests : UploadEncryptedLogError()
    object NoConnection : UploadEncryptedLogError()
    object MissingFile : UploadEncryptedLogError()
    object UnsatisfiedLinkException : UploadEncryptedLogError()
}
