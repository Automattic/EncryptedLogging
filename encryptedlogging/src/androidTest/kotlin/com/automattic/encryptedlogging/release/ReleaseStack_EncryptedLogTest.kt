package com.automattic.encryptedlogging.release

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.hamcrest.core.IsNull.nullValue
import org.junit.Test
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState.UPLOADING
import com.automattic.encryptedlogging.persistence.EncryptedLogSqlUtils
import com.automattic.encryptedlogging.store.EncryptedLogStore
import javax.inject.Inject

private const val TEST_UUID = "TEST-UUID"

class ReleaseStack_EncryptedLogTest: ReleaseStack_Base() {
    @Inject lateinit var encryptedLogStore: EncryptedLogStore
    @Inject lateinit var encryptedLogSqlUtils: EncryptedLogSqlUtils

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        mReleaseStackAppComponent.inject(this)
    }

    @Test
    fun testQueueForUpload() {
        runBlocking {
            encryptedLogStore.queueLogForUpload(TEST_UUID, createTempFile(suffix = TEST_UUID))
            assertThat(encryptedLogSqlUtils.getEncryptedLog(TEST_UUID)?.uploadState, `is`(UPLOADING))

            delay(5000)
            assertThat(encryptedLogSqlUtils.getEncryptedLog(TEST_UUID), nullValue())
        }
    }
}
