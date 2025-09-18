package com.automattic.encryptedlogging.utils

import java.util.Locale

internal object Utils {
    fun Int.toMB() = String.format(
        Locale.US,
        "%.2f",
        this.toFloat() / 1024.0 / 1024.0
    )

    fun Long.toMB() = String.format(
        Locale.US,
        "%.2f",
        this.toFloat() / 1024.0 / 1024.0
    )
}
