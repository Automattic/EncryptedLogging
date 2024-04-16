package com.automattic.encryptedlogging.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel
import com.yarolegovich.wellsql.DefaultWellConfig
import com.yarolegovich.wellsql.WellTableManager

class EncryptedWellConfig(context: Context) : DefaultWellConfig(context) {

    override fun getDbVersion(): Int {
        return 1
    }

    override fun getDbName(): String {
        return "encrypted-logging.db"
    }

    override fun getCursorWindowSize(): Long {
        return 1024
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        helper: WellTableManager?,
        oldVersion: Int,
        newVersion: Int
    ) {
        // no-op
    }

    override fun onCreate(db: SQLiteDatabase?, helper: WellTableManager?) {
        helper?.createTable(EncryptedLogModel::class.java)
    }
}
