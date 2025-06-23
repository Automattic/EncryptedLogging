package com.automattic.encryptedlogging.release

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.automattic.encryptedlogging.BuildConfig
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLoggingKey
import com.automattic.encryptedlogging.model.encryptedlogging.LogEncrypter
import com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog.EncryptedLogRestClient
import com.automattic.encryptedlogging.persistence.EncryptedLogDatabase
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.store.UploadEncryptedLogError
import com.automattic.encryptedlogging.utils.PreferenceUtils
import com.goterl.lazysodium.utils.Key
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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

    val context = InstrumentationRegistry.getInstrumentation().context
    val database = EncryptedLogDatabase.getInstance(context)
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
        nextEvent = TestEvents.NONE
        encryptedLogStore = initializeEncryptedLogStore()
    }

    @After
    fun tearDown() = runTest {
        // Reset the 'uploadState' of 'EncryptedLogStore' so that both tests can run, because it is now a singleton.
        encryptedLogStore.uploadState.value = null
        cleanSharedPreferencesState()
        database.encryptedLogDao.deleteEncryptedLogs()
    }

    @Test
    fun testQueueForUpload() = runTest {
        // GIVEN
        nextEvent = TestEvents.ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY
        val testIds = testIds()

        encryptedLogStore.uploadState.test {
            // WHEN
            testIds.forEach { uuid ->
                val payload = EncryptedLogStore.UploadEncryptedLogPayload(
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
    fun testQueueForUploadForInvalidUuid() = runTest {
        // GIVEN
        nextEvent = TestEvents.ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID

        encryptedLogStore.uploadState.test {
            // WHEN
            val payload = EncryptedLogStore.UploadEncryptedLogPayload(
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
            is OnEncryptedLogUploaded.EncryptedLogUploadedSuccessfully -> {
                assertThat(nextEvent).isEqualTo(TestEvents.ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY)
                assertThat(testIds()).contains(event.uuid)
            }

            is OnEncryptedLogUploaded.EncryptedLogFailedToUpload -> {
                when (event.error) {
                    is UploadEncryptedLogError.TooManyRequests -> {
                        // If we are hitting too many requests, we just ignore the test as restarting it will not help
                        assertThat(event.willRetry).isEqualTo(true)
                    }

                    is UploadEncryptedLogError.InvalidRequest -> {
                        assertThat(nextEvent).isEqualTo(TestEvents.ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID)
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
            com.automattic.encryptedlogging.store.ENCRYPTED_LOG_UPLOAD_UNAVAILABLE_UNTIL_DATE,
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
            database.encryptedLogDao,
            logEncrypter,
            preferenceUtils,
        )
    }
}
