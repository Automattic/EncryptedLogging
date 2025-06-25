package com.automattic.encryptedlogging.persistence.typeconverter

import androidx.room.TypeConverter
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState

internal class UploadStateTypeConverter {
    @TypeConverter
    fun fromUploadState(type: EncryptedLogUploadState) = type.value

    @TypeConverter
    fun toUploadState(value: Int) = EncryptedLogUploadState.entries.first { it.value == value }
}
