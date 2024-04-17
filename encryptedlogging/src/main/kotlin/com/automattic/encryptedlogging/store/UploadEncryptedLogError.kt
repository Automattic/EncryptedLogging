package com.automattic.encryptedlogging.store

public sealed class UploadEncryptedLogError : OnChangedError {
    public class Unknown(public val statusCode: Int? = null, public val message: String? = null) : UploadEncryptedLogError()
    public object InvalidRequest : UploadEncryptedLogError()
    public object TooManyRequests : UploadEncryptedLogError()
    public object NoConnection : UploadEncryptedLogError()
    public object MissingFile : UploadEncryptedLogError()
    public object UnsatisfiedLinkException : UploadEncryptedLogError()
}
