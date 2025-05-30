package com.automattic.encryptedlogging.store

import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLog
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState
import com.automattic.encryptedlogging.model.encryptedlogging.LogEncrypter
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.EncryptedLogRestClient
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.UploadEncryptedLogResult.LogUploadFailed
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.UploadEncryptedLogResult.LogUploaded
import com.automattic.encryptedlogging.persistence.dao.EncryptedLogDao
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded.EncryptedLogFailedToUpload
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded.EncryptedLogUploadedSuccessfully
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.InvalidRequest
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.MissingFile
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.NoConnection
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.TooManyRequests
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.Unknown
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.UnsatisfiedLinkException
import com.automattic.encryptedlogging.utils.PreferenceUtils.PreferenceUtilsWrapper
import java.io.File
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Depending on the error type, we'll keep a record of the earliest date we can try another encrypted log upload.
 *
 * The most important example of this is `TOO_MANY_REQUESTS` error which results in server refusing any uploads for
 * an hour.
 */
internal const val ENCRYPTED_LOG_UPLOAD_UNAVAILABLE_UNTIL_DATE = "ENCRYPTED_LOG_UPLOAD_UNAVAILABLE_UNTIL_DATE_PREF_KEY"
private const val UPLOAD_NEXT_DELAY = 3000L // 3 seconds
private const val TOO_MANY_REQUESTS_ERROR_DELAY = 60 * 60 * 1000L // 1 hour
private const val REGULAR_UPLOAD_FAILURE_DELAY = 60 * 1000L // 1 minute
private const val MAX_RETRY_COUNT = 3

private const val HTTP_STATUS_CODE_500 = 500
private const val HTTP_STATUS_CODE_599 = 599

internal class EncryptedLogStore(
    private val encryptedLogRestClient: EncryptedLogRestClient,
    private val logEncrypter: LogEncrypter,
    private val preferenceUtils: PreferenceUtilsWrapper,
    private val encryptedLogDao: EncryptedLogDao,
) {

    private val _uploadState = MutableStateFlow<OnEncryptedLogUploaded?>(null)
    internal val uploadState = _uploadState

    /**
     * A method for the client to use to start uploading any encrypted logs that might have been queued.
     *
     * This method should be called within a coroutine, possibly in GlobalScope so it's not attached to any one context.
     */
    @Suppress("unused")
    suspend fun uploadQueuedEncryptedLogs() {
        uploadNext()
    }

    internal suspend fun queueLogForUpload(payload: UploadEncryptedLogPayload) {
        // If the log file is not valid, there is nothing we can do
        if (!isValidFile(payload.file)) {
            _uploadState.value = EncryptedLogFailedToUpload(
                uuid = payload.uuid,
                file = payload.file,
                error = MissingFile,
                willRetry = false
            )
            return
        }
        val encryptedLog = EncryptedLog(
                uuid = payload.uuid,
                file = payload.file
        )
        encryptedLogDao.upsertEncryptedLog(
            EncryptedLogModel.fromEncryptedLog(encryptedLog)
        )

        if (payload.shouldStartUploadImmediately) {
            uploadNext()
        }
    }

    internal suspend fun resetUploadStates() {
        val encryptedLogs =
            encryptedLogDao.getEncryptedLogs(EncryptedLogUploadState.UPLOADING.value)
                .map { it.copy(uploadStateDbValue = EncryptedLogUploadState.FAILED.value) }
        encryptedLogDao.upsertEncryptedLogs(encryptedLogs)
    }

    private suspend fun uploadNextWithDelay(delay: Long) {
        addUploadDelay(delay)
        // Add a few seconds buffer to avoid possible millisecond comparison issues
        delay(delay + UPLOAD_NEXT_DELAY)
        uploadNext()
    }

    private suspend fun uploadNext() {
        if (!isUploadAvailable()) {
            return
        }
        // We want to upload a single file at a time
        val uploadStates = listOf(
            EncryptedLogUploadState.QUEUED,
            EncryptedLogUploadState.FAILED
        ).map { it.value }
        encryptedLogDao.getEncryptedLog(uploadStates)?.let {
            uploadEncryptedLog(EncryptedLog.fromEncryptedLogModel(it))
        }
    }

    @Suppress("SwallowedException")
    private suspend fun uploadEncryptedLog(encryptedLog: EncryptedLog) {
        // If the log file doesn't exist, fail immediately and try the next log file
        if (!isValidFile(encryptedLog.file)) {
            handleFailedUpload(encryptedLog, MissingFile)
            uploadNext()
            return
        }
        try {
            val encryptedText = logEncrypter.encrypt(text = encryptedLog.file.readText(), uuid = encryptedLog.uuid)

            // Update the upload state of the log
            encryptedLog.copy(uploadState = EncryptedLogUploadState.UPLOADING).let {
                encryptedLogDao.upsertEncryptedLog(EncryptedLogModel.fromEncryptedLog(it))
            }

            when (val result = encryptedLogRestClient.uploadLog(encryptedLog.uuid, encryptedText)) {
                is LogUploaded -> handleSuccessfulUpload(encryptedLog)
                is LogUploadFailed -> handleFailedUpload(encryptedLog, result.error)
            }
        } catch (e: UnsatisfiedLinkError) {
            handleFailedUpload(encryptedLog, UnsatisfiedLinkException)
        }
    }

    private suspend fun handleSuccessfulUpload(encryptedLog: EncryptedLog) {
        deleteEncryptedLog(encryptedLog)
        _uploadState.value = EncryptedLogUploadedSuccessfully(
            uuid = encryptedLog.uuid,
            file = encryptedLog.file
        )
        uploadNext()
    }

    private suspend fun handleFailedUpload(encryptedLog: EncryptedLog, error: UploadEncryptedLogError) {
        val failureType = mapUploadEncryptedLogError(error)

        val (isFinalFailure, finalFailureCount) = when (failureType) {
            EncryptedLogUploadFailureType.IRRECOVERABLE_FAILURE -> {
                Pair(true, encryptedLog.failedCount + 1)
            }
            EncryptedLogUploadFailureType.CONNECTION_FAILURE -> {
                Pair(false, encryptedLog.failedCount)
            }
            EncryptedLogUploadFailureType.CLIENT_FAILURE -> {
                val newFailedCount = encryptedLog.failedCount + 1
                Pair(newFailedCount >= MAX_RETRY_COUNT, newFailedCount)
            }
        }

        if (isFinalFailure) {
            deleteEncryptedLog(encryptedLog)
        } else {
            encryptedLogDao.upsertEncryptedLog(
                EncryptedLogModel.fromEncryptedLog(
                    encryptedLog.copy(
                        uploadState = EncryptedLogUploadState.FAILED,
                        failedCount = finalFailureCount
                    )
                )
            )
        }

        _uploadState.value = EncryptedLogFailedToUpload(
            uuid = encryptedLog.uuid,
            file = encryptedLog.file,
            error = error,
            willRetry = !isFinalFailure
        )
        // If a log failed to upload for the final time, we don't need to add any delay since the log is the problem.
        // Otherwise, the only special case that requires an extra long delay is `TOO_MANY_REQUESTS` upload error.
        if (isFinalFailure) {
            uploadNext()
        } else {
            if (error is TooManyRequests) {
                uploadNextWithDelay(TOO_MANY_REQUESTS_ERROR_DELAY)
            } else {
                uploadNextWithDelay(REGULAR_UPLOAD_FAILURE_DELAY)
            }
        }
    }

    private fun mapUploadEncryptedLogError(error: UploadEncryptedLogError): EncryptedLogUploadFailureType {
        return when (error) {
            is NoConnection -> {
                EncryptedLogUploadFailureType.CONNECTION_FAILURE
            }
            is TooManyRequests -> {
                EncryptedLogUploadFailureType.CONNECTION_FAILURE
            }
            is InvalidRequest -> {
                EncryptedLogUploadFailureType.IRRECOVERABLE_FAILURE
            }
            is MissingFile -> {
                EncryptedLogUploadFailureType.IRRECOVERABLE_FAILURE
            }
            is UnsatisfiedLinkException -> {
                EncryptedLogUploadFailureType.IRRECOVERABLE_FAILURE
            }
            is Unknown -> {
                when {
                    (HTTP_STATUS_CODE_500..HTTP_STATUS_CODE_599).contains(error.statusCode) -> {
                        EncryptedLogUploadFailureType.CONNECTION_FAILURE
                    }
                    else -> {
                        EncryptedLogUploadFailureType.CLIENT_FAILURE
                    }
                }
            }
        }
    }

    private suspend fun deleteEncryptedLog(encryptedLog: EncryptedLog) {
        encryptedLogDao.deleteEncryptedLog(EncryptedLogModel.fromEncryptedLog(encryptedLog))
    }

    private fun isValidFile(file: File): Boolean = file.exists() && file.canRead()

    /**
     * Checks if encrypted logs can be uploaded at this time.
     *
     * If we are already uploading another encrypted log or if we are manually delaying the uploads due to server errors
     * encrypted log uploads will not be available.
     */
    private suspend fun isUploadAvailable(): Boolean {
        if (encryptedLogDao.getEncryptedLogsCount(EncryptedLogUploadState.UPLOADING.value) > 0) {
            // We are already uploading another log file
            return false
        }
        preferenceUtils.getFluxCPreferences().getLong(ENCRYPTED_LOG_UPLOAD_UNAVAILABLE_UNTIL_DATE, -1L).let {
            return it <= Date().time
        }
    }

    private fun addUploadDelay(delayDuration: Long) {
        val date = Date().time + delayDuration
        preferenceUtils.getFluxCPreferences().edit().putLong(ENCRYPTED_LOG_UPLOAD_UNAVAILABLE_UNTIL_DATE, date).apply()
    }

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
    )

    /**
     * These are internal failure types to make it easier to deal with encrypted log upload errors.
     */
    private enum class EncryptedLogUploadFailureType {
        IRRECOVERABLE_FAILURE, CONNECTION_FAILURE, CLIENT_FAILURE
    }
}
