package com.automattic.encryptedlogging

import android.content.Context
import android.util.Base64
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLoggingKey
import com.automattic.encryptedlogging.model.encryptedlogging.LogEncrypter
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.EncryptedLogRestClient
import com.automattic.encryptedlogging.persistence.EncryptedLogDatabase
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.utils.PreferenceUtils
import com.goterl.lazysodium.utils.Key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

internal class AutomatticEncryptedLogging(
    context: Context,
    encryptedLoggingKey: String,
    clientSecret: String,
) : EncryptedLogging {
    private companion object {
        private const val MAX_CACHE_SIZE_IN_BYTES = 1024 * 1024 * 10
    }

    private val encryptedLogStore: EncryptedLogStore

    init {
        val cache = DiskBasedCache(
            File.createTempFile("tempcache", null),
            MAX_CACHE_SIZE_IN_BYTES
        )
        val network = BasicNetwork(HurlStack())
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }
        val encryptedLogRestClient = EncryptedLogRestClient(requestQueue, clientSecret)
        val database = EncryptedLogDatabase.getInstance(context)
        val logEncrypter = LogEncrypter(
            EncryptedLoggingKey(Key.fromBytes(Base64.decode(encryptedLoggingKey, Base64.DEFAULT)))
        )
        val preferenceUtilsWrapper = PreferenceUtils.PreferenceUtilsWrapper(
            context
        )
        encryptedLogStore = EncryptedLogStore.getInstance(
            encryptedLogRestClient,
            database.encryptedLogDao,
            logEncrypter,
            preferenceUtilsWrapper,
        )
    }

    override fun enqueueSendingEncryptedLogs(
        uuid: String,
        file: File,
        shouldUploadImmediately: Boolean,
    ) {
        val payload = EncryptedLogStore.UploadEncryptedLogPayload(
            uuid = uuid,
            file = file,
            shouldStartUploadImmediately = shouldUploadImmediately
        )
        sdkScope.launch {
            encryptedLogStore.queueLogForUpload(payload)
        }
    }

    override fun uploadEncryptedLogs() {
        sdkScope.launch {
            encryptedLogStore.uploadQueuedEncryptedLogs()
        }
    }

    override fun resetUploadStates() {
        sdkScope.launch {
            encryptedLogStore.resetUploadStates()
        }
    }

    override fun observeEncryptedLogsUploadResult(): Flow<OnEncryptedLogUploaded?> {
        return encryptedLogStore.uploadState
    }
}
