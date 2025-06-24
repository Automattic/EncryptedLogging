package com.automattic.encryptedlogging.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogEntity

@Dao
internal abstract class EncryptedLogDao {
    @Query("""
        SELECT * FROM EncryptedLogEntity 
        WHERE uploadStateDbValue IN (:uploadStates) 
        ORDER BY uploadStateDbValue ASC, id ASC 
        LIMIT 1
    """)
    internal abstract suspend fun getEncryptedLog(uploadStates: List<Int>): EncryptedLogEntity?

    @Query("""
        SELECT * FROM EncryptedLogEntity 
        WHERE uploadStateDbValue = :uploadState
    """)
    internal abstract suspend fun getEncryptedLogs(uploadState: Int): List<EncryptedLogEntity>

    @Query("""
        SELECT COUNT(*) FROM EncryptedLogEntity 
        WHERE uploadStateDbValue = :uploadState
    """)
    internal abstract suspend fun getEncryptedLogsCount(uploadState: Int): Int

    @Insert
    internal abstract suspend fun insertEncryptedLog(encryptedLog: EncryptedLogEntity)

    @Upsert
    internal abstract suspend fun upsertEncryptedLog(encryptedLog: EncryptedLogEntity)

    @Upsert
    internal abstract suspend fun upsertEncryptedLogs(encryptedLogs: List<EncryptedLogEntity>)

    @Delete
    internal abstract suspend fun deleteEncryptedLog(encryptedLog: EncryptedLogEntity)

    @Query("""
        DELETE FROM EncryptedLogEntity
    """)
    internal abstract suspend fun deleteEncryptedLogs()
}
