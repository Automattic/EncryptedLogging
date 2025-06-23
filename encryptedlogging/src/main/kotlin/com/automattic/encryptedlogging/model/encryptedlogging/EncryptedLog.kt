package com.automattic.encryptedlogging.model.encryptedlogging

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.automattic.encryptedlogging.utils.DateTimeUtils
import java.io.File
import java.util.Date

/**
 * [EncryptedLog] and [EncryptedLogModel] are tied to each other, any change in one should be reflected in the other.
 * [EncryptedLog] should be used within the app, [EncryptedLogModel] should be used for DB interactions.
 */
internal data class EncryptedLog(
    val uuid: String,
    val file: File,
    val dateCreated: Date = Date(),
    val uploadState: EncryptedLogUploadState = EncryptedLogUploadState.QUEUED,
    val failedCount: Int = 0
) {
    companion object {
        fun fromEncryptedLogModel(encryptedLogModel: EncryptedLogModel) =
            EncryptedLog(
                dateCreated = DateTimeUtils.dateUTCFromIso8601(
                    encryptedLogModel.dateCreated ?: ""
                ) ?: Date(),
                // Crash if values are missing which shouldn't happen if there are no logic errors
                uuid = encryptedLogModel.uuid!!,
                file = File(encryptedLogModel.filePath),
                uploadState = encryptedLogModel.uploadState,
                failedCount = encryptedLogModel.failedCount,
            )
    }
}

@Entity(
    tableName = "EncryptedLogEntity",
)
internal data class EncryptedLogModel(
    @PrimaryKey val uuid: String = "",
    val filePath: String? = null,
    val dateCreated: String? = null, // ISO 8601-formatted date in UTC, e.g. 1955-11-05T14:15:00Z
    val uploadStateDbValue: Int = EncryptedLogUploadState.QUEUED.value,
    val failedCount: Int = 0,
) {
    val uploadState: EncryptedLogUploadState
        get() =
            requireNotNull(
                EncryptedLogUploadState.values()
                    .firstOrNull { it.value == uploadStateDbValue }) {
                "The stateDbValue of the EncryptedLogUploadState didn't match any of the `EncryptedLogUploadState`s. " +
                        "This likely happened because the EncryptedLogUploadState values " +
                        "were altered without a DB migration."
            }

    companion object {
        fun fromEncryptedLog(encryptedLog: EncryptedLog) = EncryptedLogModel(
                uuid = encryptedLog.uuid,
                filePath = encryptedLog.file.path,
                dateCreated = DateTimeUtils.iso8601UTCFromDate(encryptedLog.dateCreated),
                uploadStateDbValue = encryptedLog.uploadState.value,
                failedCount = encryptedLog.failedCount,
        )
    }
}

internal enum class EncryptedLogUploadState(val value: Int) {
    QUEUED(1),
    UPLOADING(2),
    FAILED(3),
}
