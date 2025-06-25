package com.automattic.encryptedlogging.persistence.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLog
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogEntity
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogUploadState
import com.automattic.encryptedlogging.persistence.EncryptedLogDatabase
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.random.Random

private const val TEST_UUID = "TEST_UUID"
private const val TEST_FILE_PATH = "TEST_FILE_PATH"

@RunWith(RobolectricTestRunner::class)
class EncryptedLogDaoTest {
    private lateinit var sut: EncryptedLogDao
    private lateinit var database: EncryptedLogDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, EncryptedLogDatabase::class.java)
            .build()
        sut = database.encryptedLogDao
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun `test insert encrypted log`() = runTest {
        val uploadState = EncryptedLogUploadState.QUEUED
        val logToBeInserted = createTestEncryptedLogEntity(uploadState = uploadState)

        sut.insertEncryptedLog(logToBeInserted)

        // Assert that the encrypted log from the DB is the same as the one we inserted (ignoring id)
        assertThat(sut.getEncryptedLog(listOf(uploadState.value)))
            .usingRecursiveComparison()
            .ignoringFields("id")
            .isEqualTo(logToBeInserted)
    }

    @Test
    fun `test insert multiple encrypted logs`() = runTest {
        val uuidList = (1..5).map { "uuid-prefix-$it" }
        val uploadState = EncryptedLogUploadState.QUEUED
        val logsToBeInserted = uuidList.map { createTestEncryptedLogEntity(uuid = it, uploadState = uploadState) }

        sut.upsertEncryptedLogs(logsToBeInserted)

        // Assert that the encrypted logs from the DB is the same as the ones we inserted (ignoring id)
        uuidList.forEachIndexed { index, uuid ->
            val log = sut.getEncryptedLog(listOf(uploadState.value))
            assertThat(log)
                .usingRecursiveComparison()
                .ignoringFields("id")
                .isEqualTo(logsToBeInserted[index])
            sut.deleteEncryptedLog(log!!) // So that the next encrypted log can be fetched correctly
        }
    }

    @Test
    fun `test update encrypted log`() = runTest {
        // Insert an initial encrypted log
        val uploadState = EncryptedLogUploadState.QUEUED
        val initialLog = createTestEncryptedLogEntity(uploadState = uploadState)
        sut.insertEncryptedLog(initialLog)
        assertThat(sut.getEncryptedLog(listOf(uploadState.value)))
            .usingRecursiveComparison()
            .ignoringFields("id")
            .isEqualTo(initialLog)

        // Get the encrypted log from the database (to get the correct id)
        val initialLogFromDb = sut.getEncryptedLog(listOf(uploadState.value))
        assertThat(initialLogFromDb).isNotNull

        // Create a copy of the encrypted log by changing its upload state (which will be the common usage)
        val newUploadState = EncryptedLogUploadState.UPLOADING
        val updatedLog = initialLogFromDb?.copy(uploadStateDbValue = newUploadState.value)
        updatedLog?.let { sut.upsertEncryptedLog(updatedLog) }

        // Assert that the encrypted log in the DB is the one with the correct upload state
        val updatedLogFromDB = sut.getEncryptedLog(listOf(newUploadState.value))
        assertThat(updatedLogFromDB).isEqualTo(updatedLog)
    }

    @Test
    fun `test delete encrypted log`() = runTest {
        // Insert an initial encrypted log
        val uploadState = EncryptedLogUploadState.QUEUED
        val initialLog = createTestEncryptedLogEntity()
        sut.insertEncryptedLog(initialLog)
        assertThat(sut.getEncryptedLog(listOf(uploadState.value)))
            .usingRecursiveComparison()
            .ignoringFields("id")
            .isEqualTo(initialLog)

        // Get the encrypted log from the database (to get the correct id)
        val initialLogFromDb = sut.getEncryptedLog(listOf(uploadState.value))
        assertThat(initialLogFromDb).isNotNull

        // Delete the encrypted log
        initialLogFromDb?.let { sut.deleteEncryptedLog(it) }

        // Assert that the encrypted log no longer exists
        assertThat(sut.getEncryptedLog(listOf(uploadState.value))).isNull()
    }

    @Test
    fun `test get uploading encrypted logs`() = runTest {
        // Insert an encrypted log with uuid
        val logToBeInserted = createTestEncryptedLogEntity(uploadState = EncryptedLogUploadState.UPLOADING)
        sut.insertEncryptedLog(logToBeInserted)

        // Assert that the encrypted log from the DB is the same as the one we inserted (ignoring id)
        val uploadingEncryptedLogs = sut.getEncryptedLogs(
            EncryptedLogUploadState.UPLOADING.value
        )
        assertThat(uploadingEncryptedLogs).hasSize(1)
        assertThat(uploadingEncryptedLogs.first())
            .usingRecursiveComparison()
            .ignoringFields("id")
            .isEqualTo(logToBeInserted)
    }

    @Test
    fun `test uploading encrypted logs count for empty DB`() = runTest {
        assertThat(sut.getEncryptedLogsCount(EncryptedLogUploadState.UPLOADING.value)).isEqualTo(0)
    }

    @Test
    fun `test uploading encrypted logs for random number`() = runTest {
        val numberOfLogs = 10
        repeat(numberOfLogs) {
            sut.insertEncryptedLog(
                createTestEncryptedLogEntity(
                    uuid = UUID.randomUUID().toString(),
                    uploadState = EncryptedLogUploadState.UPLOADING
                )
            )
        }
        assertThat(sut.getEncryptedLogsCount(EncryptedLogUploadState.UPLOADING.value))
            .isEqualTo(numberOfLogs.toLong())
    }

    @Test
    fun `test get encrypted logs for upload is in correct order`() = runTest {
        sut.insertEncryptedLog(createTestEncryptedLogEntity(uploadState = EncryptedLogUploadState.FAILED))
        sut.insertEncryptedLog(createTestEncryptedLogEntity(uploadState = EncryptedLogUploadState.QUEUED))

        // Queued logs should be uploaded before the failed ones
        val uploadStates = listOf(
            EncryptedLogUploadState.QUEUED,
            EncryptedLogUploadState.FAILED
        ).map { it.value }
        assertThat(sut.getEncryptedLog(uploadStates)?.uploadState)
            .isEqualTo(EncryptedLogUploadState.QUEUED)
    }

    /* HELPER FUNCTIONS */

    private fun createTestEncryptedLog(
        uuid: String = TEST_UUID,
        filePath: String = TEST_FILE_PATH,
        uploadState: EncryptedLogUploadState = EncryptedLogUploadState.QUEUED
    ) = EncryptedLog(
        uuid = uuid,
        file = File(filePath),
        // Bypass the annoying milliseconds comparison issue
        uploadState = uploadState
    )

    private fun createTestEncryptedLogEntity(
        uuid: String = TEST_UUID,
        filePath: String = TEST_FILE_PATH,
        uploadState: EncryptedLogUploadState = EncryptedLogUploadState.QUEUED
    ) = EncryptedLogEntity.fromEncryptedLog(
        createTestEncryptedLog(
            uuid = uuid,
            filePath = filePath,
            uploadState = uploadState
        )
    )
}
