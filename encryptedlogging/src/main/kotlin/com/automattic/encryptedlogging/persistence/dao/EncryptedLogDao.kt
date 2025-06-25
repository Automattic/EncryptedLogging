package com.automattic.encryptedlogging.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogEntity
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState

@Dao
internal abstract class EncryptedLogDao {
    @Query(
        """
        SELECT * FROM EncryptedLogEntity 
        WHERE uploadState IN (:uploadStates) 
        ORDER BY uploadState ASC, id ASC 
        LIMIT 1
    """
    )
    internal abstract suspend fun getEncryptedLog(vararg uploadStates: EncryptedLogUploadState): EncryptedLogEntity?

    @Query(
        """
        SELECT * FROM EncryptedLogEntity 
        WHERE uploadState = :uploadState
    """
    )
    internal abstract suspend fun getEncryptedLogs(uploadState: EncryptedLogUploadState): List<EncryptedLogEntity>

    @Query(
        """
        SELECT COUNT(*) FROM EncryptedLogEntity 
        WHERE uploadState = :uploadState
    """
    )
    internal abstract suspend fun getEncryptedLogsCount(uploadState: EncryptedLogUploadState): Int

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
