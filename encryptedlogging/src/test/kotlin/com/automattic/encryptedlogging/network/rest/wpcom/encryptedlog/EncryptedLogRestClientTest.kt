package com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog

import com.automattic.encryptedlogging.network.EncryptedLogHttpClient
import com.automattic.encryptedlogging.store.UploadEncryptedLogError
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EncryptedLogRestClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var restClient: EncryptedLogRestClient

    private val testClientSecret = "test-secret"
    private val testLogUuid = "test-uuid-123"
    private val testContents = "encrypted log contents"

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val httpClient = EncryptedLogHttpClient(
            testClientSecret,
            mockWebServer.url("/rest/v1.1/encrypted-logging/").toString()
        )
        restClient = EncryptedLogRestClient(httpClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `uploadLog sends correct request with headers and body`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        runBlocking {
            restClient.uploadLog(testLogUuid, testContents)
        }

        mockWebServer.takeRequest().let { request ->
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/rest/v1.1/encrypted-logging/")
            assertThat(request.getHeader("Content-Type")).isEqualTo("application/json")
            assertThat(request.getHeader("Authorization")).isEqualTo(testClientSecret)
            assertThat(request.getHeader("log-uuid")).isEqualTo(testLogUuid)
            assertThat(request.body.readUtf8()).isEqualTo(testContents)
        }
    }

    @Test
    fun `uploadLog returns LogUploaded on success`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = runBlocking {
            restClient.uploadLog(testLogUuid, testContents)
        }

        assertThat(result).isInstanceOf(UploadEncryptedLogResult.LogUploaded::class.java)
    }

    @Test
    fun `uploadLog returns InvalidRequest error for invalid-request error type`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""
            {
                "error": "invalid-request",
                "message": "Invalid UUID: uuids must only contain letters, numbers, dashes, and curly brackets"
            }
        """.trimIndent()))

        val result = runBlocking {
            restClient.uploadLog(testLogUuid, testContents)
        }

        assertThat((result as UploadEncryptedLogResult.LogUploadFailed).error)
            .isInstanceOf(UploadEncryptedLogError.InvalidRequest::class.java)
    }

    @Test
    fun `uploadLog returns TooManyRequests error for too_many_requests error type`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("""
            {
                "error": "too_many_requests",
                "message": "You're sending too many messages. Please slow down."
            }
        """.trimIndent()))

        val result = runBlocking {
            restClient.uploadLog(testLogUuid, testContents)
        }

        assertThat((result as UploadEncryptedLogResult.LogUploadFailed).error)
            .isInstanceOf(UploadEncryptedLogError.TooManyRequests::class.java)
    }

    @Test
    fun `uploadLog returns Unknown error for unrecognized error type`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("""
            {
                "error": "some-other-error",
                "message": "Some other error occurred"
            }
        """.trimIndent()))

        val result = runBlocking {
            restClient.uploadLog(testLogUuid, testContents)
        }

        ((result as UploadEncryptedLogResult.LogUploadFailed).error as UploadEncryptedLogError.Unknown).let { error ->
            assertThat(error.statusCode).isEqualTo(500)
            assertThat(error.message).isEqualTo("Some other error occurred")
        }
    }

    @Test
    fun `uploadLog returns Unknown error with statusCode and message for non-JSON error response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Plain text error message"))

        val result = runBlocking {
            restClient.uploadLog(testLogUuid, testContents)
        }

        ((result as UploadEncryptedLogResult.LogUploadFailed).error as UploadEncryptedLogError.Unknown).let { error ->
            assertThat(error.statusCode).isEqualTo(500)
            assertThat(error.message).isEqualTo("Plain text error message")
        }
    }

    @Test
    fun `uploadLog returns NoConnection error when IOException occurs`() {
        mockWebServer.shutdown()

        val result = runBlocking {
            restClient.uploadLog(testLogUuid, testContents)
        }

        assertThat((result as UploadEncryptedLogResult.LogUploadFailed).error)
            .isInstanceOf(UploadEncryptedLogError.NoConnection::class.java)
    }
}
