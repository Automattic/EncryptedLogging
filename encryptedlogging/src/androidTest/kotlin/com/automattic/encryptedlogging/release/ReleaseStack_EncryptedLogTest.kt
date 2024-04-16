package com.automattic.encryptedlogging.release

import com.automattic.encryptedlogging.release.ReleaseStack_EncryptedLogTest.TestEvents.ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY
import com.automattic.encryptedlogging.release.ReleaseStack_EncryptedLogTest.TestEvents.ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID
import com.automattic.encryptedlogging.store.Dispatcher
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded.EncryptedLogFailedToUpload
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded.EncryptedLogUploadedSuccessfully
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.InvalidRequest
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.TooManyRequests
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogPayload
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import org.greenrobot.eventbus.Subscribe
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.wordpress.android.fluxc.generated.EncryptedLogActionBuilder

private const val NUMBER_OF_LOGS_TO_UPLOAD = 2
private const val TEST_UUID_PREFIX = "TEST-UUID-"
private const val INVALID_UUID = "INVALID_UUID" // Underscore is not allowed

class ReleaseStack_EncryptedLogTest {
    lateinit var encryptedLogStore: EncryptedLogStore

    private var nextEvent: TestEvents? = null
    lateinit var mCountDownLatch: CountDownLatch

    private val mDispatcher: Dispatcher = Dispatcher()

    private enum class TestEvents {
        NONE,
        ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY,
        ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID
    }

    @Before
    fun setUp() {
        mDispatcher.register(this)
        nextEvent = TestEvents.NONE
    }

    @Test
    @Ignore("Disabling as a part of effort to exclude flaky or failing tests." +
        "See https://github.com/wordpress-mobile/WordPress-FluxC-Android/pull/2665")
    fun testQueueForUpload() {
        nextEvent = ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY

        val testIds = testIds()
        mCountDownLatch = CountDownLatch(testIds.size)
        testIds.forEach { uuid ->
            val payload = UploadEncryptedLogPayload(
                    uuid = uuid,
                    file = createTempFileWithContent(suffix = uuid, content = "Testing FluxC log upload for $uuid"),
                    shouldStartUploadImmediately = true
            )
            mDispatcher.dispatch(EncryptedLogActionBuilder.newUploadLogAction(payload))
        }
        assertTrue(mCountDownLatch.await(30.seconds.inWholeMilliseconds, TimeUnit.MILLISECONDS))
    }

    @Test
    @Ignore("While 'testQueueForUpload' passes, this test fails and thus temporarily ignored")
    fun testQueueForUploadForInvalidUuid() {
        nextEvent = ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID

        mCountDownLatch = CountDownLatch(1)
        val payload = UploadEncryptedLogPayload(
                uuid = INVALID_UUID,
                file = createTempFile(suffix = INVALID_UUID),
                shouldStartUploadImmediately = true
        )
        mDispatcher.dispatch(EncryptedLogActionBuilder.newUploadLogAction(payload))
        assertTrue(mCountDownLatch.await(30.seconds.inWholeMilliseconds, TimeUnit.MILLISECONDS))
    }

    @Suppress("unused")
    @Subscribe
    fun onEncryptedLogUploaded(event: OnEncryptedLogUploaded) {
        when (event) {
            is EncryptedLogUploadedSuccessfully -> {
                assertThat(nextEvent, `is`(ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY))
                assertThat(testIds(), hasItem(event.uuid))
            }
            is EncryptedLogFailedToUpload -> {
                when (event.error) {
                    is TooManyRequests -> {
                        // If we are hitting too many requests, we just ignore the test as restarting it will not help
                        assertThat(event.willRetry, `is`(true))
                    }
                    is InvalidRequest -> {
                        assertThat(nextEvent, `is`(ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID))
                        assertThat(event.willRetry, `is`(false))
                    }
                    else -> {
                        throw AssertionError("Unexpected error occurred in onEncryptedLogUploaded: ${event.error}")
                    }
                }
            }
        }
        mCountDownLatch.countDown()
    }

    private fun testIds() = (1..NUMBER_OF_LOGS_TO_UPLOAD).map { i ->
        "$TEST_UUID_PREFIX$i"
    }

    private fun createTempFileWithContent(suffix: String, content: String): File {
        val file = createTempFile(suffix = suffix)
        file.writeText(content)
        return file
    }
}
