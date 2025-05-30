package com.automattic.encryptedlogging.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel
import com.automattic.encryptedlogging.persistence.dao.EncryptedLogDao

internal const val DATABASE_VERSION = 1

@Database(
    version = DATABASE_VERSION,
    entities = [
        EncryptedLogModel::class,
    ],
    autoMigrations = []
)
@TypeConverters(
    value = []
)
internal abstract class EncryptedLogDatabase : RoomDatabase() {
    internal abstract val encryptedLogDao: EncryptedLogDao

    companion object {
        fun buildDb(applicationContext: Context) = Room.databaseBuilder(
            applicationContext,
            EncryptedLogDatabase::class.java,
            "encrypted-log.db"
        ).allowMainThreadQueries()
            .build()
    }
}
