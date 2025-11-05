package com.automattic.encryptedlogging.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val AUTHORIZATION_HEADER = "Authorization"
private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val CONTENT_TYPE_JSON = "application/json"
private const val UUID_HEADER = "log-uuid"
private const val UPLOAD_URL = "https://public-api.wordpress.com/rest/v1.1/encrypted-logging/"

internal class EncryptedLogHttpClient(
    private val clientSecret: String
) {
    @Throws(IOException::class)
    fun uploadLog(logUuid: String, contents: String): HttpResponse {
        val url = URL(UPLOAD_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
            setRequestProperty(AUTHORIZATION_HEADER, clientSecret)
            setRequestProperty(UUID_HEADER, logUuid)
        }

        try {
            connection.outputStream.use { outputStream ->
                outputStream.write(contents.toByteArray())
                outputStream.flush()
            }

            val statusCode = connection.responseCode
            val responseBody = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            return HttpResponse(statusCode, responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

internal data class HttpResponse(
    val statusCode: Int,
    val body: String
)
