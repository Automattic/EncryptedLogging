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
import com.automattic.encryptedlogging.model.encryptedlogging.LogEncrypter
import com.automattic.encryptedlogging.network.BaseRequest.BaseNetworkError
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.EncryptedLogRestClient
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.UploadEncryptedLogResult.LogUploadFailed
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.UploadEncryptedLogResult.LogUploaded
import com.automattic.encryptedlogging.persistence.EncryptedLogSqlUtils
import com.automattic.encryptedlogging.store.EncryptedLogStore.EncryptedLogUploadFailureType.CLIENT_FAILURE
import com.automattic.encryptedlogging.store.EncryptedLogStore.EncryptedLogUploadFailureType.CONNECTION_FAILURE
import com.automattic.encryptedlogging.store.EncryptedLogStore.EncryptedLogUploadFailureType.IRRECOVERABLE_FAILURE
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded.EncryptedLogFailedToUpload
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded.EncryptedLogUploadedSuccessfully
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.InvalidRequest
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.MissingFile
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.NoConnection
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.TooManyRequests
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.Unknown
import com.automattic.encryptedlogging.tools.CoroutineEngine
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.API
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delay to be used in between uploads whether they are successful or failure. This should help us avoid
 * `TOO_MANY_REQUESTS` error in typical situations.
 */
private const val UPLOAD_DELAY = 30000L
private const val MAX_RETRY_COUNT = 3

@Singleton
class EncryptedLogStore @Inject constructor(
    private val encryptedLogRestClient: EncryptedLogRestClient,
    private val encryptedLogSqlUtils: EncryptedLogSqlUtils,
    private val coroutineEngine: CoroutineEngine,
    private val logEncrypter: LogEncrypter,
    dispatcher: Dispatcher
) : Store(dispatcher) {
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
        // If the log file is not valid, there is nothing we can do
        if (!isValidFile(payload.file)) {
            emitChange(
                    EncryptedLogFailedToUpload(
                            uuid = payload.uuid,
                            file = payload.file,
                            error = MissingFile,
                            willRetry = false
                    )
            )
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

    private suspend fun uploadNextWithDelay() {
        delay(UPLOAD_DELAY)
        uploadNext()
    }

    private suspend fun uploadNext() {
        if (encryptedLogSqlUtils.getNumberOfUploadingEncryptedLogs() > 0) {
            // We are already uploading another log file
            return
        }
        // We want to upload a single file at a time
        encryptedLogSqlUtils.getEncryptedLogsForUpload().firstOrNull()?.let {
            uploadEncryptedLog(it)
        }
    }

    private suspend fun uploadEncryptedLog(encryptedLog: EncryptedLog) {
        // If the log file doesn't exist, fail immediately and try the next log file
        if (!isValidFile(encryptedLog.file)) {
            handleFailedUpload(encryptedLog, MissingFile)
            uploadNext()
            return
        }
        val encryptedText = logEncrypter.encrypt(text = encryptedLog.file.readText(), uuid = encryptedLog.uuid)

        // Update the upload state of the log
        encryptedLog.copy(uploadState = UPLOADING).let {
            encryptedLogSqlUtils.insertOrUpdateEncryptedLog(it)
        }

        when (val result = encryptedLogRestClient.uploadLog(encryptedLog.uuid, encryptedText)) {
            is LogUploaded -> handleSuccessfulUpload(encryptedLog)
            is LogUploadFailed -> handleFailedUpload(encryptedLog, result.error)
        }
        uploadNextWithDelay()
    }

    private fun handleSuccessfulUpload(encryptedLog: EncryptedLog) {
        deleteEncryptedLog(encryptedLog)
        emitChange(EncryptedLogUploadedSuccessfully(uuid = encryptedLog.uuid, file = encryptedLog.file))
    }

    private fun handleFailedUpload(encryptedLog: EncryptedLog, error: UploadEncryptedLogError) {
        val failureType = mapUploadEncryptedLogError(error)

        val (isFinalFailure, finalFailureCount) = when (failureType) {
            IRRECOVERABLE_FAILURE -> {
                Pair(true, encryptedLog.failedCount + 1)
            }
            CONNECTION_FAILURE -> {
                Pair(false, encryptedLog.failedCount)
            }
            CLIENT_FAILURE -> {
                val newFailedCount = encryptedLog.failedCount + 1
                Pair(newFailedCount >= MAX_RETRY_COUNT, newFailedCount)
            }
        }

        if (isFinalFailure) {
            deleteEncryptedLog(encryptedLog)
        } else {
            encryptedLogSqlUtils.insertOrUpdateEncryptedLog(
                    encryptedLog.copy(
                            uploadState = FAILED,
                            failedCount = finalFailureCount
                    )
            )
        }

        emitChange(
                EncryptedLogFailedToUpload(
                        uuid = encryptedLog.uuid,
                        file = encryptedLog.file,
                        error = error,
                        willRetry = !isFinalFailure
                )
        )
    }

    private fun mapUploadEncryptedLogError(error: UploadEncryptedLogError): EncryptedLogUploadFailureType {
        return when (error) {
            is NoConnection -> {
                CONNECTION_FAILURE
            }
            is TooManyRequests -> {
                CONNECTION_FAILURE
            }
            is InvalidRequest -> {
                IRRECOVERABLE_FAILURE
            }
            is MissingFile -> {
                IRRECOVERABLE_FAILURE
            }
            is Unknown -> {
                when {
                    (500..599).contains(error.statusCode) -> {
                        CONNECTION_FAILURE
                    }
                    else -> {
                        CLIENT_FAILURE
                    }
                }
            }
        }
    }

    private fun deleteEncryptedLog(encryptedLog: EncryptedLog) {
        encryptedLogSqlUtils.deleteEncryptedLogs(listOf(encryptedLog))
    }

    private fun isValidFile(file: File): Boolean = file.exists() && file.canRead()

    /**
     * Payload to be used to queue a file to be encrypted and uploaded.
     *
     * [shouldStartUploadImmediately] property will be used by [EncryptedLogStore] to decide whether the encryption and
     * upload should be initiated immediately. Since the main use case to queue a log file to be uploaded is a crash,
     * the default value is `false`. If we try to upload the log file during a crash, there won't be enough time to
     * encrypt and upload it, which means it'll just fail. On the other hand, for developer initiated crash monitoring
     * events, it'd be good, but not essential, to set it to `true` so we can upload it as soon as possible.
     */
    class UploadEncryptedLogPayload(
        val uuid: String,
        val file: File,
        val shouldStartUploadImmediately: Boolean = false
    ) : Payload<BaseNetworkError>()

    sealed class OnEncryptedLogUploaded(val uuid: String, val file: File) : Store.OnChanged<UploadEncryptedLogError>() {
        class EncryptedLogUploadedSuccessfully(uuid: String, file: File) : OnEncryptedLogUploaded(uuid, file)
        class EncryptedLogFailedToUpload(
            uuid: String,
            file: File,
            error: UploadEncryptedLogError,
            val willRetry: Boolean
        ) : OnEncryptedLogUploaded(uuid, file) {
            init {
                this.error = error
            }
        }
    }

    sealed class UploadEncryptedLogError : OnChangedError {
        class Unknown(val statusCode: Int? = null, val message: String? = null) : UploadEncryptedLogError()
        object InvalidRequest : UploadEncryptedLogError()
        object TooManyRequests : UploadEncryptedLogError()
        object NoConnection : UploadEncryptedLogError()
        object MissingFile : UploadEncryptedLogError()
    }

    /**
     * These are internal failure types to make it easier to deal with encrypted log upload errors.
     */
    private enum class EncryptedLogUploadFailureType {
        IRRECOVERABLE_FAILURE, CONNECTION_FAILURE, CLIENT_FAILURE
    }
}
