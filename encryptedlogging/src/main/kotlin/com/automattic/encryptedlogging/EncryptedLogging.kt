package com.automattic.encryptedlogging

import android.content.Context
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded
import kotlinx.coroutines.flow.Flow
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

    /**
     * A method for the client to use to observe the upload result of the encrypted logs.
     */
    public fun observeEncryptedLogsUploadResult(): Flow<OnEncryptedLogUploaded?>

    public companion object {

        private var instance: EncryptedLogging? = null

        /**
         * Creates an instance of [EncryptedLogging] using the provided context, encrypted logging key and client
         * secret.
         *
         * @param context The Android context to use for initializing the logging system.
         * @param encryptedLoggingKey The key used for encrypting logs.
         * @param clientSecret The secret used for authenticating requests to the logging server.
         * @return An instance of [EncryptedLogging].
         */
        public fun getInstance(
            context: Context,
            encryptedLoggingKey: String,
            clientSecret: String,
        ): EncryptedLogging {
            if (instance == null) {
                instance = AutomatticEncryptedLogging(
                    context = context,
                    encryptedLoggingKey = encryptedLoggingKey,
                    clientSecret = clientSecret
                )
            }
            return checkNotNull(instance) { "Failed to create EncryptedLogging instance" }
        }
    }
}
