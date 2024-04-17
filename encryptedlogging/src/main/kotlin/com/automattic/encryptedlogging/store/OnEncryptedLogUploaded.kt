package com.automattic.encryptedlogging.store

import java.io.File

public sealed class OnEncryptedLogUploaded(public val uuid: String, public val file: File) : Store.OnChanged<UploadEncryptedLogError>() {
    public class EncryptedLogUploadedSuccessfully(uuid: String, file: File) : OnEncryptedLogUploaded(uuid, file)
    public class EncryptedLogFailedToUpload(
        uuid: String,
        file: File,
        error: UploadEncryptedLogError,
        internal val willRetry: Boolean
    ) : OnEncryptedLogUploaded(uuid, file) {
        init {
            this.error = error
        }
    }
}
