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
import com.automattic.encryptedlogging.store.EncryptedLogStore
import com.automattic.encryptedlogging.store.EncryptedLogStore.OnEncryptedLogUploaded
import com.automattic.encryptedlogging.store.EncryptedLogStore.UploadEncryptedLogPayload
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val NUMBER_OF_LOGS_TO_UPLOAD = 3
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

        val testIds = testIds()
        mCountDownLatch = CountDownLatch(testIds.size)
        testIds.forEach { uuid ->
            val payload = UploadEncryptedLogPayload(uuid = uuid, file = createTempFile(suffix = uuid))
            mDispatcher.dispatch(EncryptedLogActionBuilder.newUploadLogAction(payload))
        }
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Suppress("unused")
    @Subscribe
    fun onEncryptedLogUploaded(event: OnEncryptedLogUploaded) {
        assertThat("Unexpected error occurred in onEncryptedLogUploaded: ${event.error}",
                event.isError, `is`(false))
        assertThat(nextEvent, `is`(ENCRYPTED_LOG_UPLOADED_SUCCESSFULLY))
        assertThat(testIds(), hasItem(event.uuid))
        mCountDownLatch.countDown()
    }

    private fun testIds() = (1..NUMBER_OF_LOGS_TO_UPLOAD).map { i ->
        "$TEST_UUID-$i"
    }
}
