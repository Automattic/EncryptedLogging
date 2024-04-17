package com.automattic.encryptedlogging.utils

import android.content.Context
import android.content.SharedPreferences

internal object PreferenceUtils {
    @JvmStatic
    fun getFluxCPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("${context.packageName}_fluxc-preferences", Context.MODE_PRIVATE)
    }

    class PreferenceUtilsWrapper(private val context: Context) {
        fun getFluxCPreferences(): SharedPreferences {
            return PreferenceUtils.getFluxCPreferences(context)
        }
    }
}
