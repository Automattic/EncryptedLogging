package com.automattic.encryptedlogging.release

import org.greenrobot.eventbus.Subscribe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItem
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import com.automattic.encryptedlogging.TestUtils
import com.automattic.encryptedlogging.generated.EncryptedLogActionBuilder
import com.automattic.encryptedlogging.release.ReleaseStack_EncryptedLogTest.TestEvents.ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY
import com.automattic.encryptedlogging.release.ReleaseStack_EncryptedLogTest.TestEvents.ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.InvalidRequest
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogError.TooManyRequests
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogPayload
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val NUMBER_OF_LOGS_TO_UPLOAD = 3
private const val TEST_UUID_PREFIX = "TEST-UUID-"
private const val INVALID_UUID = "INVALID_UUID" // Underscore is not allowed

class ReleaseStack_EncryptedLogTest : ReleaseStack_Base() {
    @Inject lateinit var encryptedLogStore: EncryptedLogStore

    private var nextEvent: TestEvents? = null

    private enum class TestEvents {
        NONE,
        ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY,
        ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        mReleaseStackAppComponent.inject(this)
        init()
        nextEvent = TestEvents.NONE
    }

    @Test
    fun testQueueForUpload() {
        nextEvent = ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY

        val testIds = testIds()
        mCountDownLatch = CountDownLatch(testIds.size)
        testIds.forEach { uuid ->
            val payload = UploadEncryptedLogPayload(uuid = uuid, file = createTempFile(suffix = uuid))
            mDispatcher.dispatch(EncryptedLogActionBuilder.newUploadLogAction(payload))
        }
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Test
    fun testQueueForUploadForInvalidUuid() {
        nextEvent = ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID

        mCountDownLatch = CountDownLatch(1)
        val payload = UploadEncryptedLogPayload(uuid = INVALID_UUID, file = createTempFile(suffix = INVALID_UUID))
        mDispatcher.dispatch(EncryptedLogActionBuilder.newUploadLogAction(payload))
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Suppress("unused")
    @Subscribe
    fun onEncryptedLogUploaded(event: OnEncryptedLogUploaded) {
        if (event.isError) {
            when (event.error) {
                is TooManyRequests -> {
                    // If we are hitting too many requests error, there really isn't much we can do about it
                }
                is InvalidRequest -> {
                    assertThat(nextEvent, `is`(ENCRYPTED_LOG_UPLOAD_FAILED_WITH_INVALID_UUID))
                }
                else -> {
                    throw AssertionError("Unexpected error occurred in onEncryptedLogUploaded: ${event.error}")
                }
            }
        } else {
            assertThat(nextEvent, `is`(ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY))
            assertThat(testIds(), hasItem(event.uuid))
        }
        mCountDownLatch.countDown()
    }

    private fun testIds() = (1..NUMBER_OF_LOGS_TO_UPLOAD).map { i ->
        "$TEST_UUID_PREFIX$i"
    }
}
