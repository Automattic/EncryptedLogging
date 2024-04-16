package com.automattic.encryptedlogging.encryptedlog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel
import com.yarolegovich.wellsql.DefaultWellConfig
import com.yarolegovich.wellsql.WellTableManager
import com.yarolegovich.wellsql.core.Identifiable

class TestConfig(context: Context) : DefaultWellConfig(context) {

    fun reset() {
        //todo
    }

    override fun getDbVersion(): Int {
        return 1
    }

    override fun getDbName(): String {
        return "test.db"
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
        //do nothing
    }

    override fun onCreate(db: SQLiteDatabase?, helper: WellTableManager?) {
        helper?.createTable(EncryptedLogModel::class.java)
    }

}
