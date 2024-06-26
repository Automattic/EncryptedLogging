package com.automattic.encryptedlogging.model.encryptedlogging

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid

/**
 * Convenience helpers for Encrypted Logging
 */
internal object EncryptionUtils {
    /**
     * Use a single shared instance of the Sodium library.
     *
     * The initialization is inexpensive, but verbose, so this is just syntactic sugar.
     */
    @JvmStatic
    val sodium = LazySodiumAndroid(SodiumAndroid())
}
