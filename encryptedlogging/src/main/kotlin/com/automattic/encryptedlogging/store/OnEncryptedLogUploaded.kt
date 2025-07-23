package com.automattic.encryptedlogging.store

import java.io.File

public sealed class OnEncryptedLogUploaded(
    public val uuid: String,
    public val file: File
) {
    public class EncryptedLogUploadedSuccessfully(
        uuid: String,
        file: File
    ) : OnEncryptedLogUploaded(uuid, file)

    public class EncryptedLogFailedToUpload(
        uuid: String,
        file: File,
        public val error: UploadEncryptedLogError,
        public val willRetry: Boolean
    ) : OnEncryptedLogUploaded(uuid, file)
}
