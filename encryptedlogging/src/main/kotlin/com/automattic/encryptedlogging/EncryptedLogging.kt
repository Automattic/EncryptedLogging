package com.automattic.encryptedlogging

import java.io.File

public interface EncryptedLogging {
    /**
     * A method for the client to use to enqueue encrypted logs for sending.
     */
    public fun enqueueSendingEncryptedLogs(
        uuid: String,
        file: File,
        shouldUploadImmediately: Boolean,
    )

    /**
     * A method for the client to use to start uploading any encrypted logs that might have been queued.
     *
     * This method should be called within a coroutine, possibly in GlobalScope so it's not attached to any one context.
     */
    public fun uploadEncryptedLogs()

    /**
     * A method for the client to use to reset the upload states. Usually called on app initialization, before [uploadEncryptedLogs]
     */
    public fun resetUploadStates()
}
