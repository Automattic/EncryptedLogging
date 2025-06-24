package com.automattic.encryptedlogging.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogEntity
import com.automattic.encryptedlogging.persistence.dao.EncryptedLogDao

private const val DATABASE_VERSION = 1
private const val DATABASE_NAME = "encrypted-log.db"

@Database(
    version = DATABASE_VERSION,
    entities = [
        EncryptedLogEntity::class,
    ],
)
internal abstract class EncryptedLogDatabase : RoomDatabase() {
    internal abstract val encryptedLogDao: EncryptedLogDao

    companion object {
        private var instance: EncryptedLogDatabase? = null

        fun getInstance(context: Context): EncryptedLogDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context,
                    EncryptedLogDatabase::class.java,
                    DATABASE_NAME
                ).build()
            }
            return checkNotNull(instance) { "EncryptedLogDatabase instance is null, this should never happen." }
        }
    }
}
