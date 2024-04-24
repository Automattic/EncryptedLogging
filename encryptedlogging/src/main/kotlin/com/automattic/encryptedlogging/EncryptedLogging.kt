package com.automattic.encryptedlogging

import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded
import java.io.File
import kotlinx.coroutines.flow.StateFlow

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
    public suspend fun uploadEncryptedLogs()

    /**
     * A method for the client to use to reset the upload states. Usually called on app initialization, before [uploadEncryptedLogs]
     */
    public fun resetUploadStates()

    /**
     * A method for the client to use to observe the upload result of the encrypted logs.
     */
    public fun observeEncryptedLogsUploadResult(): StateFlow<OnEncryptedLogUploaded?>
}
