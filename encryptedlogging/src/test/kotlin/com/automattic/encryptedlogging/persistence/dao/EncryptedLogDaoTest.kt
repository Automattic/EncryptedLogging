package com.automattic.encryptedlogging.persistence.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLog
import com.automattic.encryptedlogging.model.encryptedlogging.EncryptedLogModel
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
import java.time.temporal.ChronoUnit.SECONDS
import java.util.Date
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
            .allowMainThreadQueries()
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
        // Assert that there are no encrypted logs with the test uuid
        assertThat(sut.getEncryptedLog(TEST_UUID)).isNull()

        // Insert an encrypted log with uuid
        val logToBeInserted = EncryptedLogModel.fromEncryptedLog(createTestEncryptedLog())
        sut.upsertEncryptedLog(logToBeInserted)

        // Assert that the encrypted log from the DB is the same as the one we inserted
        assertThat(sut.getEncryptedLog(TEST_UUID)).isEqualTo(logToBeInserted)
    }

    @Test
    fun `test insert multiple encrypted logs`() = runTest {
        // Assert that there are no encrypted logs with the test uuid
        assertThat(sut.getEncryptedLog(TEST_UUID)).isNull()

        // Insert an encrypted log with uuid
        val uuidList = (1..5).map { "uuid-prefix-$it" }
        val logsToBeInserted = uuidList.map {
            createTestEncryptedLog(uuid = it)
        }.map { EncryptedLogModel.fromEncryptedLog(it) }
        sut.upsertEncryptedLogs(logsToBeInserted)

        // Assert that the encrypted logs from the DB is the same as the ones we inserted
        uuidList.forEachIndexed { index, uuid ->
            val log = sut.getEncryptedLog(uuid)
            assertThat(log).isEqualTo(logsToBeInserted[index])
        }
    }

    @Test
    fun `test update encrypted log`() = runTest {
        // Insert an initial encrypted log
        val initialLog = EncryptedLogModel.fromEncryptedLog(createTestEncryptedLog())
        sut.upsertEncryptedLog(initialLog)
        assertThat(sut.getEncryptedLog(TEST_UUID)).isEqualTo(initialLog)

        // Create a copy of the encrypted log by changing its upload state (which will be the common usage)
        val newUploadState = EncryptedLogUploadState.UPLOADING
        val updatedLog = initialLog.copy(uploadStateDbValue = newUploadState.value)
        sut.upsertEncryptedLog(updatedLog)

        // Assert that the encrypted log in the DB is the one with the correct upload state
        val updatedLogFromDB = sut.getEncryptedLog(TEST_UUID)
        assertThat(requireNotNull(updatedLogFromDB?.uploadState)).isEqualTo(newUploadState)
        // This verifies the expected state as well but separating the initial assertion is valuable to show intent
        assertThat(updatedLogFromDB).isEqualTo(updatedLog)
    }

    @Test
    fun `test delete encrypted log`() = runTest {
        // Insert an initial encrypted log
        val initialLog = EncryptedLogModel.fromEncryptedLog(createTestEncryptedLog())
        sut.upsertEncryptedLog(initialLog)
        assertThat(sut.getEncryptedLog(TEST_UUID)).isEqualTo(initialLog)

        // Delete the encrypted log
        sut.deleteEncryptedLog(initialLog)

        // Assert that the encrypted log no longer exists
        assertThat(sut.getEncryptedLog(TEST_UUID)).isNull()
    }

    @Test
    fun `test get uploading encrypted logs`() = runTest {
        // Insert an encrypted log with uuid
        val logToBeInserted = EncryptedLogModel.fromEncryptedLog(createTestEncryptedLog(
            uploadState = EncryptedLogUploadState.UPLOADING)
        )
        sut.upsertEncryptedLog(logToBeInserted)

        // Assert that the encrypted log from the DB is the same as the one we inserted
        val uploadingEncryptedLogs = sut.getEncryptedLogs(
            EncryptedLogUploadState.UPLOADING.value
        )
        assertThat(uploadingEncryptedLogs).hasSize(1)
        assertThat(uploadingEncryptedLogs.first()).isEqualTo(logToBeInserted)
    }

    @Test
    fun `test uploading encrypted logs count for empty DB`() = runTest {
        assertThat(sut.getEncryptedLogsCount(EncryptedLogUploadState.UPLOADING.value)).isEqualTo(0)
    }

    @Test
    fun `test uploading encrypted logs for random number`() = runTest {
        Random.nextInt(100).let { numberOfLogs ->
            repeat(numberOfLogs) {
                sut.upsertEncryptedLog(
                    EncryptedLogModel.fromEncryptedLog(
                        createTestEncryptedLog(
                            uuid = UUID.randomUUID().toString(),
                            uploadState = EncryptedLogUploadState.UPLOADING
                        )
                    )
                )
            }
            assertThat(sut.getEncryptedLogsCount(EncryptedLogUploadState.UPLOADING.value))
                .isEqualTo(numberOfLogs.toLong())
        }
    }

    @Test
    fun `test get encrypted logs for upload includes QUEUED logs`() = runTest {
        sut.upsertEncryptedLog(
            EncryptedLogModel.fromEncryptedLog(
                createTestEncryptedLog(
                    uploadState = EncryptedLogUploadState.QUEUED
                )
            )
        )

        val uploadStates = listOf(
            EncryptedLogUploadState.QUEUED,
            EncryptedLogUploadState.FAILED
        ).map { it.value }
        assertThat(sut.getEncryptedLog(uploadStates)).isNotNull
    }

    @Test
    fun `test get encrypted logs for upload includes FAILED logs`() = runTest {
        sut.upsertEncryptedLog(
            EncryptedLogModel.fromEncryptedLog(
                createTestEncryptedLog(uploadState = EncryptedLogUploadState.FAILED)
            )
        )

        val uploadStates = listOf(
            EncryptedLogUploadState.QUEUED,
            EncryptedLogUploadState.FAILED
        ).map { it.value }
        assertThat(sut.getEncryptedLog(uploadStates)).isNotNull
    }

    @Test
    fun `test get encrypted logs for upload does not include UPLOADING logs`() = runTest {
        sut.upsertEncryptedLog(
            EncryptedLogModel.fromEncryptedLog(
                createTestEncryptedLog(uploadState = EncryptedLogUploadState.UPLOADING)
            )
        )

        val uploadStates = listOf(
            EncryptedLogUploadState.QUEUED,
            EncryptedLogUploadState.FAILED
        ).map { it.value }
        assertThat(sut.getEncryptedLog(uploadStates)).isNull()
    }

    @Test
    fun `test get encrypted logs for upload is in correct order`() = runTest {
        sut.upsertEncryptedLog(
            EncryptedLogModel.fromEncryptedLog(
                createTestEncryptedLog(uploadState = EncryptedLogUploadState.FAILED)
            )
        )
        sut.upsertEncryptedLog(
            EncryptedLogModel.fromEncryptedLog(
                createTestEncryptedLog(uploadState = EncryptedLogUploadState.QUEUED)
            )
        )

        // Queued logs should be uploaded before the failed ones
        val uploadStates = listOf(
            EncryptedLogUploadState.QUEUED,
            EncryptedLogUploadState.FAILED
        ).map { it.value }
        assertThat(sut.getEncryptedLog(uploadStates)?.uploadState)
            .isEqualTo(EncryptedLogUploadState.QUEUED)
    }

    private fun createTestEncryptedLog(
        uuid: String = TEST_UUID,
        filePath: String = TEST_FILE_PATH,
        dateCreated: Date = Date(),
        uploadState: EncryptedLogUploadState = EncryptedLogUploadState.QUEUED
    ) = EncryptedLog(
        uuid = uuid,
        file = File(filePath),
        // Bypass the annoying milliseconds comparison issue
        dateCreated = Date.from(dateCreated.toInstant().truncatedTo(SECONDS)),
        uploadState = uploadState
    )
}