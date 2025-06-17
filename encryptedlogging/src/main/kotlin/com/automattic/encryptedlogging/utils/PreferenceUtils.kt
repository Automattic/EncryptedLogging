package com.automattic.encryptedlogging.utils

import android.content.Context
import android.content.SharedPreferences

internal object PreferenceUtils {
    @JvmStatic
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("${context.packageName}_encrypted-log-preferences", Context.MODE_PRIVATE)
    }

    class PreferenceUtilsWrapper(private val context: Context) {
        fun getPreferences(): SharedPreferences {
            return getPreferences(context)
        }
    }
}
