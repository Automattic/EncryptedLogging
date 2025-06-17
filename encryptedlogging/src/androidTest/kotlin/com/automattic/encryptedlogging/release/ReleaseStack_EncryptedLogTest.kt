package com.automattic.encryptedlogging.release

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.automattic.encryptedlogging.BuildConfig
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLoggingKey
import com.automattic.encryptedlogging.model.encryptedlogging.LogEncrypter
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.EncryptedLogRestClient
import com.automattic.encryptedlogging.persistence.EncryptedLogSqlUtils
import com.automattic.encryptedlogging.persistence.EncryptedWellConfig
import com.automattic.encryptedlogging.release.ReleaseStack_EncryptedLogTest.TestEvents.ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY
import com.automattic.encryptedlogging.release.ReleaseStack_EncryptedLogTest.TestEvents.ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID
import com.automattic.encryptedlogging.store.ENCRYPTED_LOG_UPLOAD_UNAVAILABLE_UNTIL_DATE
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogPayload
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded.EncryptedLogFailedToUpload
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded.EncryptedLogUploadedSuccessfully
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.InvalidRequest
import com.automattic.encryptedlogging.store.UploadEncryptedLogError.TooManyRequests
import com.automattic.encryptedlogging.utils.PreferenceUtils
import com.goterl.lazysodium.utils.Key
import com.yarolegovich.wellsql.WellSql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

private const val NUMBER_OF_LOGS_TO_UPLOAD = 2
private const val TEST_UUID_PREFIX = "TEST-UUID-"
private const val INVALID_UUID = "INVALID_UUID" // Underscore is not allowed

@OptIn(ExperimentalCoroutinesApi::class)
internal class ReleaseStack_EncryptedLogTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    val context = InstrumentationRegistry.getInstrumentation().context
    val preferenceUtils = PreferenceUtils.PreferenceUtilsWrapper(context)
    lateinit var encryptedLogStore: EncryptedLogStore

    private var nextEvent: TestEvents? = null

    private enum class TestEvents {
        NONE,
        ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY,
        ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nextEvent = TestEvents.NONE
        encryptedLogStore = initializeEncryptedLogStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Reset the 'uploadState' of 'EncryptedLogStore' so that both tests can run, because it is now a singleton.
        encryptedLogStore.uploadState.value = null
        cleanSharedPreferencesState()
        WellSql.delete(EncryptedLogModel::class.java).execute()
    }

    @Test
    fun testQueueForUpload() = testScope.runTest {
        // GIVEN
        nextEvent = ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY
        val testIds = testIds()

        encryptedLogStore.uploadState.test {
            // WHEN
            testIds.forEach { uuid ->
                val payload = UploadEncryptedLogPayload(
                    uuid = uuid,
                    file = createTempFileWithContent(
                        suffix = uuid,
                        content = "Testing log upload for $uuid at ${System.currentTimeMillis()}"
                    ),
                    shouldStartUploadImmediately = true
                )
                encryptedLogStore.queueLogForUpload(payload)
            }

            // THEN
            // First '_uploadState' event is null due to initialization, thus repeat once + number of logs to upload.
            repeat(1 + NUMBER_OF_LOGS_TO_UPLOAD) {
                val event: OnEncryptedLogUploaded? = awaitItem()
                event?.let { onEncryptedLogUploaded(it) }
            }
        }
    }

    @Test
    fun testQueueForUploadForInvalidUuid() = testScope.runTest {
        // GIVEN
        nextEvent = ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID

        encryptedLogStore.uploadState.test {
            // WHEN
            val payload = UploadEncryptedLogPayload(
                uuid = INVALID_UUID,
                file = File.createTempFile("test", INVALID_UUID),
                shouldStartUploadImmediately = true
            )
            encryptedLogStore.queueLogForUpload(payload)

            // THEN
            // First '_uploadState' event is 'null' due to initialization, thus repeat twice.
            repeat(2) {
                val event: OnEncryptedLogUploaded? = awaitItem()
                event?.let { onEncryptedLogUploaded(it) }
            }
        }
    }

    private fun onEncryptedLogUploaded(event: OnEncryptedLogUploaded) {
        when (event) {
            is EncryptedLogUploadedSuccessfully -> {
                assertThat(nextEvent).isEqualTo(ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY)
                assertThat(testIds()).contains(event.uuid)
            }

            is EncryptedLogFailedToUpload -> {
                when (event.error) {
                    is TooManyRequests -> {
                        // If we are hitting too many requests, we just ignore the test as restarting it will not help
                        assertThat(event.willRetry).isEqualTo(true)
                    }

                    is InvalidRequest -> {
                        assertThat(nextEvent).isEqualTo(ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID)
                        assertThat(event.willRetry).isEqualTo(false)
                    }

                    else -> {
                        throw AssertionError("Unexpected error occurred in onEncryptedLogUploaded: ${event.error}")
                    }
                }
            }
        }
    }

    private fun testIds() = (1..NUMBER_OF_LOGS_TO_UPLOAD).map { i ->
        "$TEST_UUID_PREFIX$i"
    }

    private fun createTempFileWithContent(suffix: String, content: String): File {
        val file = File.createTempFile("test", suffix)
        file.writeText(content)
        return file
    }

    private fun cleanSharedPreferencesState() {
        preferenceUtils.getPreferences().edit().putLong(
            ENCRYPTED_LOG_UPLOAD_UNAVAILABLE_UNTIL_DATE,
            -1
        ).commit()
    }

    private fun initializeEncryptedLogStore(): EncryptedLogStore {
        val cache = DiskBasedCache(
            File.createTempFile("tempcache", null),
            1024 * 1024 // 1MB cap
        )
        val network = BasicNetwork(HurlStack())
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }
        val encryptedLogRestClient = EncryptedLogRestClient(requestQueue, BuildConfig.APP_SECRET)
        val encryptedLogSqlUtils = EncryptedLogSqlUtils()

        val key = EncryptedLoggingKey(
            Key.fromBytes(
                Base64.decode(
                    BuildConfig.ENCRYPTION_KEY,
                    Base64.DEFAULT
                )
            )
        )
        val logEncrypter = LogEncrypter(key)
        return EncryptedLogStore.getInstance(
            encryptedLogRestClient,
            encryptedLogSqlUtils,
            logEncrypter,
            preferenceUtils,
            EncryptedWellConfig(context)
        )
    }
}
