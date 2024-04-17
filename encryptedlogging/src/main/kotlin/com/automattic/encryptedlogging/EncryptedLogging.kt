package com.automattic.encryptedlogging

import android.content.Context
import android.util.Base64
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLoggingKey
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptionUtils
import com.automattic.encryptedlogging.model.encryptedlogging.LogEncrypter
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.EncryptedLogRestClient
import com.automattic.encryptedlogging.persistence.EncryptedLogSqlUtils
import com.automattic.encryptedlogging.persistence.EncryptedWellConfig
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded.EncryptedLogFailedToUpload
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded.EncryptedLogUploadedSuccessfully
import com.automattic.encryptedlogging.store.Store
import com.automattic.encryptedlogging.utils.PreferenceUtils
import com.goterl.lazysodium.utils.Key
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.generated.EncryptedLogActionBuilder

class EncryptedLogging(
    context: Context,
    encryptedLoggingKey: String,
    clientSecret: String,
) {

    private val dispatcher = Dispatcher()
    private val encryptedLogStore: EncryptedLogStore

    val uploadState =
        MutableStateFlow<Store.OnChanged<EncryptedLogStore.UploadEncryptedLogError>?>(null)

    init {
        dispatcher.register(this)
        val cache = DiskBasedCache(File.createTempFile("tempcache", null), 1024 * 1024) // 1MB cap
        val network = BasicNetwork(HurlStack())
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }
        val encryptedLogRestClient = EncryptedLogRestClient(requestQueue, clientSecret)
        val encryptedLogSqlUtils = EncryptedLogSqlUtils()
        val encryptedLoggingKey = EncryptedLoggingKey(
            Key.fromBytes(
                Base64.decode(
                    encryptedLoggingKey,
                    Base64.DEFAULT
                )
            )
        )
        val logEncrypter = LogEncrypter(encryptedLoggingKey)
        val preferenceUtilsWrapper = PreferenceUtils.PreferenceUtilsWrapper(
            context
        )
        encryptedLogStore = EncryptedLogStore(
            encryptedLogRestClient,
            encryptedLogSqlUtils,
            logEncrypter,
            preferenceUtilsWrapper,
            dispatcher,
            EncryptedWellConfig(context)
        )
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onEncryptedLogUploaded(event: OnEncryptedLogUploaded) {
        uploadState.value = event
    }

    fun enqueueSendingEncryptedLogs(
        uuid: String,
        file: File,
        shouldUploadImmediately: Boolean,
    ) {
        val payload = EncryptedLogStore.UploadEncryptedLogPayload(
            uuid = uuid,
            file = file,
            shouldStartUploadImmediately = shouldUploadImmediately
        )
        dispatcher.dispatch(EncryptedLogActionBuilder.newUploadLogAction(payload))
    }
}
