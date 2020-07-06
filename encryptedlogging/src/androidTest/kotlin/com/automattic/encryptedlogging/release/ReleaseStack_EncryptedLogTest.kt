package com.automattic.encryptedlogging.release

import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import com.automattic.encryptedlogging.TestUtils
import com.automattic.encryptedlogging.generated.EncryptedLogActionBuilder
import com.automattic.encryptedlogging.release.ReleaseStack_EncryptedLogTest.TestEvents.ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogPayload
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TEST_UUID = "TEST-UUID"

class ReleaseStack_EncryptedLogTest : ReleaseStack_Base() {
    @Inject lateinit var encryptedLogStore: EncryptedLogStore

    private var nextEvent: TestEvents? = null

    private enum class TestEvents {
        NONE,
        ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY
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

        val payload = UploadEncryptedLogPayload(uuid = TEST_UUID, file = createTempFile(suffix = TEST_UUID))
        mCountDownLatch = CountDownLatch(1)
        mDispatcher.dispatch(EncryptedLogActionBuilder.newUploadLogAction(payload))
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Suppress("unused")
    @Subscribe
    fun onEncryptedLogUploaded(event: OnEncryptedLogUploaded) {
        if (event.isError) {
            throw AssertionError("Unexpected error occurred in onEncryptedLogUploaded: ${event.error}")
        } else {
            assertEquals(nextEvent, ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY)
        }
        mCountDownLatch.countDown()
    }
}
