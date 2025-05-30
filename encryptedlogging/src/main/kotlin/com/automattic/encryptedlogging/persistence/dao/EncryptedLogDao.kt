package com.automattic.encryptedlogging.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel

@Dao
internal abstract class EncryptedLogDao {
    @Upsert
    internal abstract suspend fun upsertEncryptedLog(encryptedLog: EncryptedLogModel)

    @Upsert
    internal abstract suspend fun upsertEncryptedLogs(encryptedLogs: List<EncryptedLogModel>)

    @Query("""
        SELECT * FROM EncryptedLogEntity 
        WHERE uuid = :uuid
    """)
    internal abstract suspend fun getEncryptedLog(uuid: String): EncryptedLogModel?

    @Query("""
        SELECT * FROM EncryptedLogEntity 
        WHERE uploadStateDbValue IN (:uploadStates) 
        ORDER BY uploadStateDbValue ASC, dateCreated ASC 
        LIMIT 1
    """)
    internal abstract suspend fun getEncryptedLog(uploadStates: List<Int>): EncryptedLogModel?

    @Query("""
        SELECT * FROM EncryptedLogEntity 
        WHERE uploadStateDbValue = :uploadState
    """)
    internal abstract suspend fun getEncryptedLogs(uploadState: Int): List<EncryptedLogModel>

    @Query("""
        SELECT COUNT(*) FROM EncryptedLogEntity 
        WHERE uploadStateDbValue = :uploadState
    """)
    internal abstract suspend fun getEncryptedLogsCount(uploadState: Int): Int

    @Delete
    abstract suspend fun deleteEncryptedLog(encryptedLog: EncryptedLogModel)

    @Delete
    abstract suspend fun deleteEncryptedLogs(encryptedLogs: List<EncryptedLogModel>)
}
