package com.automattic.encryptedlogging.encryptedlog

import com.yarolegovich.wellsql.WellSql
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.automattic.encryptedlogging.SingleStoreWellSqlConfigForTests
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLog
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState.FAILED
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState.QUEUED
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState.UPLOADING
import com.automattic.encryptedlogging.persistence.EncryptedLogSqlUtils
import java.io.File
import java.time.temporal.ChronoUnit.SECONDS
import java.util.Date
import java.util.UUID
import kotlin.random.Random

private const val TEST_UUID = "TEST_UUID"
private const val TEST_FILE_PATH = "TEST_FILE_PATH"

@RunWith(RobolectricTestRunner::class)
class EncryptedLogSqlUtilsTest {
    private lateinit var sqlUtils: EncryptedLogSqlUtils

    @Before
    fun setUp() {
        val appContext = RuntimeEnvironment.application.applicationContext
        val config = SingleStoreWellSqlConfigForTests(appContext, EncryptedLogModel::class.java)
        WellSql.init(config)
        config.reset()

        sqlUtils = EncryptedLogSqlUtils()
    }

    @Test
    fun `test insert encrypted log`() {
        // Assert that there are no encrypted logs with the test uuid
        assertThat(getTestEncryptedLogFromDB()).isNull()

        // Insert an encrypted log with uuid
        val logToBeInserted = createTestEncryptedLog()
        sqlUtils.insertOrUpdateEncryptedLog(logToBeInserted)

        // Assert that the encrypted log from the DB is the same as the one we inserted
        val log = getTestEncryptedLogFromDB()
        assertThat(log).isEqualToComparingFieldByField(logToBeInserted)
    }

    @Test
    fun `test update encrypted log`() {
        // Insert an initial encrypted log
        val initialLog = createTestEncryptedLog()
        sqlUtils.insertOrUpdateEncryptedLog(initialLog)
        assertThat(getTestEncryptedLogFromDB()).isEqualToComparingFieldByField(initialLog)

        // Create a copy of the encrypted log by changing its upload state (which will be the common usage)
        val newUploadState = EncryptedLogUploadState.UPLOADING
        val updatedLog = initialLog.copy(uploadState = newUploadState)
        sqlUtils.insertOrUpdateEncryptedLog(updatedLog)

        // Assert that the encrypted log in the DB is the one with the correct upload state
        val updatedLogFromDB = getTestEncryptedLogFromDB()
        assertThat(requireNotNull(updatedLogFromDB?.uploadState)).isEqualTo(newUploadState)
        // This verifies the expected state as well but separating the initial assertion is valuable to show intent
        assertThat(updatedLogFromDB).isEqualToComparingFieldByField(updatedLog)
    }

    @Test
    fun `test delete encrypted log`() {
        // Insert an initial encrypted log
        val initialLog = createTestEncryptedLog()
        sqlUtils.insertOrUpdateEncryptedLog(initialLog)
        assertThat(getTestEncryptedLogFromDB()).isEqualToComparingFieldByField(initialLog)

        // Delete the encrypted log
        sqlUtils.deleteEncryptedLogs(listOf(initialLog))

        // Assert that the encrypted log no longer exists
        assertThat(getTestEncryptedLogFromDB()).isNull()
    }

    @Test
    fun `test uploading encrypted logs count for empty DB`() {
        assertThat(sqlUtils.getNumberOfUploadingEncryptedLogs()).isEqualTo(0)
    }

    @Test
    fun `test uploading encrypted logs for random number`() {
        Random.nextInt(100).let { numberOfLogs ->
            repeat(numberOfLogs) {
                sqlUtils.insertOrUpdateEncryptedLog(
                        createTestEncryptedLog(
                                uuid = UUID.randomUUID().toString(),
                                uploadState = UPLOADING
                        )
                )
            }
            assertThat(sqlUtils.getNumberOfUploadingEncryptedLogs()).isEqualTo(numberOfLogs.toLong())
        }
    }

    @Test
    fun `test get encrypted logs for upload includes QUEUED logs`() {
        sqlUtils.insertOrUpdateEncryptedLog(createTestEncryptedLog(uploadState = QUEUED))

        assertThat(sqlUtils.getEncryptedLogsForUpload()).isNotEmpty
    }

    @Test
    fun `test get encrypted logs for upload includes FAILED logs`() {
        sqlUtils.insertOrUpdateEncryptedLog(createTestEncryptedLog(uploadState = FAILED))

        assertThat(sqlUtils.getEncryptedLogsForUpload()).isNotEmpty
    }

    @Test
    fun `test get encrypted logs for upload does not include UPLOADING logs`() {
        sqlUtils.insertOrUpdateEncryptedLog(createTestEncryptedLog(uploadState = UPLOADING))

        assertThat(sqlUtils.getEncryptedLogsForUpload()).isEmpty()
    }

    @Test
    fun `test get encrypted logs for upload is in correct order`() {
        sqlUtils.insertOrUpdateEncryptedLog(createTestEncryptedLog(uploadState = FAILED))
        sqlUtils.insertOrUpdateEncryptedLog(createTestEncryptedLog(uploadState = QUEUED))

        // Queued logs should be uploaded before the failed ones
        assertThat(sqlUtils.getEncryptedLogsForUpload().firstOrNull()?.uploadState).isEqualTo(QUEUED)
    }

    private fun getTestEncryptedLogFromDB() = sqlUtils.getEncryptedLog(TEST_UUID)

    private fun createTestEncryptedLog(
        uuid: String = TEST_UUID,
        filePath: String = TEST_FILE_PATH,
        dateCreated: Date = Date(),
        uploadState: EncryptedLogUploadState = QUEUED
    ) = EncryptedLog(
            // Bypass the annoying milliseconds comparison issue
            uuid = uuid,
            file = File(filePath),
            dateCreated = Date.from(dateCreated.toInstant().truncatedTo(SECONDS)),
            uploadState = uploadState
    )
}
