package com.automattic.encryptedlogging.model.encryptedlogging

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

/**
 * [EncryptedLog] and [EncryptedLogEntity] are tied to each other, any change in one should be reflected in the other.
 * [EncryptedLog] should be used within the app, [EncryptedLogEntity] should be used for DB interactions.
 */
internal data class EncryptedLog(
    val id: Int = 0,
    val uuid: String,
    val file: File,
    val uploadState: EncryptedLogUploadState = EncryptedLogUploadState.QUEUED,
    val failedCount: Int = 0
) {
    companion object {
        fun fromEncryptedLogEntity(encryptedLogEntity: EncryptedLogEntity) =
            EncryptedLog(
                id = encryptedLogEntity.id,
                uuid = checkNotNull(encryptedLogEntity.uuid) { "UUID cannot be null" },
                file = File(checkNotNull(encryptedLogEntity.filePath) { "File path cannot be null" }),
                uploadState = encryptedLogEntity.uploadState,
                failedCount = encryptedLogEntity.failedCount,
            )
    }
}

@Entity(
    tableName = "EncryptedLogEntity",
)
internal data class EncryptedLogEntity(
    /**
     * Synthetic primary key used to effectively order encrypted logs without depending on the date created.
     */
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uuid: String? = null,
    val filePath: String? = null,
    val uploadState: EncryptedLogUploadState = EncryptedLogUploadState.QUEUED,
    val failedCount: Int = 0,
) {

    companion object Companion {
        fun fromEncryptedLog(encryptedLog: EncryptedLog) = EncryptedLogEntity(
            id = encryptedLog.id,
            uuid = encryptedLog.uuid,
            filePath = encryptedLog.file.path,
            uploadState = encryptedLog.uploadState,
            failedCount = encryptedLog.failedCount,
        )
    }
}

internal enum class EncryptedLogUploadState(val value: Int) {
    QUEUED(1),
    UPLOADING(2),
    FAILED(3),
}
