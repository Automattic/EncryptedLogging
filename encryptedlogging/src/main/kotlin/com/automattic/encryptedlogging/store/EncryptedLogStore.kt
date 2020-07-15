package com.automattic.encryptedlogging.store

import kotlinx.coroutines.delay
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.automattic.encryptedlogging.Dispatcher
import com.automattic.encryptedlogging.Payload
import com.automattic.encryptedlogging.action.EncryptedLogAction
import com.automattic.encryptedlogging.action.EncryptedLogAction.RESET_UPLOAD_STATES
import com.automattic.encryptedlogging.action.EncryptedLogAction.UPLOAD_LOG
import com.automattic.encryptedlogging.annotations.action.Action
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLog
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState.FAILED
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState.UPLOADING
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptionUtils
import com.automattic.encryptedlogging.model.encryptedlogging.LogEncrypter
import com.automattic.encryptedlogging.network.BaseRequest.BaseNetworkError
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.EncryptedLogRestClient
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.UploadEncryptedLogResult.LogUploadFailed
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.UploadEncryptedLogResult.LogUploaded
import com.automattic.encryptedlogging.persistence.EncryptedLogSqlUtils
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.InvalidRequest
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.TooManyRequests
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.Unknown
import com.automattic.encryptedlogging.tools.CoroutineEngine
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.API
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_RETRY_COUNT = 3

@Singleton
class EncryptedLogStore @Inject constructor(
    private val encryptedLogRestClient: EncryptedLogRestClient,
    private val encryptedLogSqlUtils: EncryptedLogSqlUtils,
    private val coroutineEngine: CoroutineEngine,
    dispatcher: Dispatcher
) : Store(dispatcher) {
    private val keyPair = EncryptionUtils.sodium.cryptoBoxKeypair()

    override fun onRegister() {
        AppLog.d(API, this.javaClass.name + ": onRegister")
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    override fun onAction(action: Action<*>) {
        val actionType = action.type as? EncryptedLogAction ?: return
        when (actionType) {
            UPLOAD_LOG -> {
                coroutineEngine.launch(API, this, "EncryptedLogStore: On UPLOAD_LOG") {
                    queueLogForUpload(action.payload as UploadEncryptedLogPayload)
                }
            }
            RESET_UPLOAD_STATES -> {
                coroutineEngine.launch(API, this, "EncryptedLogStore: On RESET_UPLOAD_STATES") {
                    resetUploadStates()
                }
            }
        }
    }

    /**
     * A method for the client to use to start uploading any encrypted logs that might have been queued.
     *
     * This method should be called within a coroutine, possibly in GlobalScope so it's not attached to any one context.
     */
    @Suppress("unused")
    suspend fun uploadQueuedEncryptedLogs() {
        uploadNext()
    }

    private suspend fun queueLogForUpload(payload: UploadEncryptedLogPayload) {
        // If the log file doesn't exist, there is nothing we can do
        if (!payload.file.exists()) {
            return
        }
        val encryptedLog = EncryptedLog(
                uuid = payload.uuid,
                file = payload.file
        )
        encryptedLogSqlUtils.insertOrUpdateEncryptedLog(encryptedLog)

        if (payload.shouldStartUploadImmediately) {
            uploadNext()
        }
    }

    private fun resetUploadStates() {
        encryptedLogSqlUtils.insertOrUpdateEncryptedLogs(encryptedLogSqlUtils.getUploadingEncryptedLogs().map {
            it.copy(uploadState = FAILED)
        })
    }

    private suspend fun uploadNextWithBackOffTiming() {
        // TODO: Add a proper back off timing logic
        delay(10000)
        uploadNext()
    }

    private suspend fun uploadNext() {
        if (encryptedLogSqlUtils.getNumberOfUploadingEncryptedLogs() > 0) {
            // We are already uploading another log file
            return
        }
        val (logsToUpload, logsToDelete) = encryptedLogSqlUtils.getEncryptedLogsForUpload()
                .partition { it.file.exists() }
        // Delete any queued encrypted log records if the log file no longer exists
        encryptedLogSqlUtils.deleteEncryptedLogs(logsToDelete)
        // We want to upload a single file at a time
        logsToUpload.firstOrNull()?.let {
            uploadEncryptedLog(it)
        }
    }

    private suspend fun uploadEncryptedLog(encryptedLog: EncryptedLog) {
        // Update the upload state of the log
        encryptedLog.copy(uploadState = UPLOADING).let {
            encryptedLogSqlUtils.insertOrUpdateEncryptedLog(it)
        }
        val contents = LogEncrypter(
                sourceFile = encryptedLog.file,
                uuid = encryptedLog.uuid,
                publicKey = keyPair.publicKey
        ).encrypt()
        when (val result = encryptedLogRestClient.uploadLog(encryptedLog.uuid, contents)) {
            is LogUploaded -> handleSuccessfulUpload(encryptedLog)
            is LogUploadFailed -> handleFailedUpload(encryptedLog, result.error)
        }
    }

    private suspend fun handleSuccessfulUpload(encryptedLog: EncryptedLog) {
        deleteEncryptedLog(encryptedLog)
        emitChange(OnEncryptedLogUploaded(uuid = encryptedLog.uuid, file = encryptedLog.file))
        uploadNext()
    }

    private suspend fun handleFailedUpload(encryptedLog: EncryptedLog, error: UploadEncryptedLogError) {
        val handleServerError = {
            encryptedLogSqlUtils.insertOrUpdateEncryptedLog(encryptedLog.copy(uploadState = FAILED))
        }
        when (error) {
            is TooManyRequests -> {
                handleServerError.invoke()
            }
            is InvalidRequest -> {
                handleFinalUploadFailure(encryptedLog, error)
            }
            is Unknown -> {
                when {
                    // If it's a server error, we should just retry later
                    (500..599).contains(error.statusCode) -> {
                        handleServerError.invoke()
                    }
                    encryptedLog.failedCount + 1 >= MAX_RETRY_COUNT -> {
                        handleFinalUploadFailure(encryptedLog, error)
                    }
                    else -> {
                        encryptedLogSqlUtils.insertOrUpdateEncryptedLog(encryptedLog.copy(
                                failedCount = encryptedLog.failedCount + 1,
                                uploadState = FAILED
                        ))
                    }
                }
            }
        }
        uploadNextWithBackOffTiming()
    }

    /**
     * If a log has failed to upload too many times, or it's failing for a reason we know retrying won't help,
     * this method should be called to clean up and notify the client.
     */
    private fun handleFinalUploadFailure(encryptedLog: EncryptedLog, error: UploadEncryptedLogError) {
        deleteEncryptedLog(encryptedLog)

        // Since we have a retry mechanism we should only notify that we failed to upload when we give up
        emitChange(OnEncryptedLogUploaded(uuid = encryptedLog.uuid, file = encryptedLog.file, error = error))
    }

    private fun deleteEncryptedLog(encryptedLog: EncryptedLog) {
        encryptedLogSqlUtils.deleteEncryptedLogs(listOf(encryptedLog))
    }

    /**
     * Payload to be used to queue a file to be encrypted and uploaded.
     *
     * [shouldStartUploadImmediately] property will be used by [EncryptedLogStore] to decide whether the encryption and
     * upload should be initiated immediately. Since the main use case to queue a log file to be uploaded is a crash,
     * the default value is `false`. If we try to upload the log file during a crash, there won't be enough time to
     * encrypt and upload it, which means it'll just fail.
     */
    class UploadEncryptedLogPayload(
        val uuid: String,
        val file: File,
        val shouldStartUploadImmediately: Boolean = false
    ) : Payload<BaseNetworkError>()

    class OnEncryptedLogUploaded(
        val uuid: String,
        val file: File,
        error: UploadEncryptedLogError? = null
    ) : Store.OnChanged<UploadEncryptedLogError>() {
        init {
            this.error = error
        }
    }

    sealed class UploadEncryptedLogError(val statusCode: Int?, val message: String?) : OnChangedError {
        class Unknown(statusCode: Int? = null, message: String? = null) : UploadEncryptedLogError(statusCode, message)
        class InvalidRequest(statusCode: Int?, message: String?) : UploadEncryptedLogError(statusCode, message)
        class TooManyRequests(statusCode: Int?, message: String?) : UploadEncryptedLogError(statusCode, message)
    }
}
