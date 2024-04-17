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
import com.automattic.encryptedlogging.persistence.EncryptedLogSqlUtils
import com.automattic.encryptedlogging.persistence.EncryptedWellConfig
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.utils.PreferenceUtils
import com.goterl.lazysodium.utils.Key
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.generated.EncryptedLogActionBuilder

public class EncryptedLogging(
    context: Context,
    encryptedLoggingKey: String,
    clientSecret: String,
) {

    private val dispatcher = Dispatcher()
    private val encryptedLogStore: EncryptedLogStore
    private val uploadState = MutableStateFlow<OnEncryptedLogUploaded?>(null)

    init {
        dispatcher.register(this)
        val cache = DiskBasedCache(File.createTempFile("tempcache", null), 1024 * 1024 * 10)
        val network = BasicNetwork(HurlStack())
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }
        val encryptedLogRestClient = EncryptedLogRestClient(requestQueue, clientSecret)
        val encryptedLogSqlUtils = EncryptedLogSqlUtils()
        val logEncrypter = LogEncrypter(
            EncryptedLoggingKey(Key.fromBytes(Base64.decode(encryptedLoggingKey, Base64.DEFAULT)))
        )
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
    internal fun onEncryptedLogUploaded(event: OnEncryptedLogUploaded) {
        uploadState.value = event
    }

    public fun enqueueSendingEncryptedLogs(
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

    /**
     * A method for the client to use to start uploading any encrypted logs that might have been queued.
     *
     * This method should be called within a coroutine, possibly in GlobalScope so it's not attached to any one context.
     */
    public suspend fun uploadEncryptedLogs() {
        encryptedLogStore.uploadQueuedEncryptedLogs()
    }

    /**
     * A method for the client to use to reset the upload states. Usually called on app initialization, before [uploadEncryptedLogs]
     */
    public fun resetUploadStates() {
        dispatcher.dispatch(EncryptedLogActionBuilder.newResetUploadStatesAction())
    }

    /**
     * A method for the client to use to observe the upload result of the encrypted logs.
     */
    public fun observeEncryptedLogsUploadResult(): StateFlow<OnEncryptedLogUploaded?> {
        return uploadState
    }
}
