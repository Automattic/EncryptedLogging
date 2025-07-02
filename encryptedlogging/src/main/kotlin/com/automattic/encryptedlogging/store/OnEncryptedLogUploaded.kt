package com.automattic.encryptedlogging.store

import java.io.File

internal sealed class OnEncryptedLogUploaded(
    val uuid: String,
    val file: File
) : OnChanged<UploadEncryptedLogError>() {
    class EncryptedLogUploadedSuccessfully(uuid: String, file: File) : OnEncryptedLogUploaded(uuid, file)
    class EncryptedLogFailedToUpload(
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
