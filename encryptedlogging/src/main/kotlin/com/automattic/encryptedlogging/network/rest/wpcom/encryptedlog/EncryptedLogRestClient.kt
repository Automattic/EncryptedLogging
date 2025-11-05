package com.automattic.encryptedlogging.network.rest.wpcom.encryptedlog

import android.util.Log
import com.automattic.encryptedlogging.network.EncryptedLogHttpClient
import com.automattic.encryptedlogging.store.UploadEncryptedLogError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

private const val INVALID_REQUEST = "invalid-request"
private const val TOO_MANY_REQUESTS = "too_many_requests"

internal class EncryptedLogRestClient(
    private val httpClient: EncryptedLogHttpClient,
) {
    suspend fun uploadLog(logUuid: String, contents: String): UploadEncryptedLogResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.uploadLog(logUuid, contents)
                if (response.statusCode in 200..299) {
                    UploadEncryptedLogResult.LogUploaded
                } else {
                    UploadEncryptedLogResult.LogUploadFailed(mapError(response.statusCode, response.body))
                }
            } catch (e: IOException) {
                UploadEncryptedLogResult.LogUploadFailed(UploadEncryptedLogError.NoConnection)
            }
        }
    }

    @Suppress("ReturnCount")
    private fun mapError(statusCode: Int, responseBody: String): UploadEncryptedLogError {
        val json = try {
            JSONObject(responseBody)
        } catch (jsonException: JSONException) {
            Log.e(TAG, "Received response not in JSON format: " + jsonException.message)
            return UploadEncryptedLogError.Unknown(message = responseBody)
        }
        val errorMessage = json.getString("message")
        json.getString("error").let { errorType ->
            if (errorType == INVALID_REQUEST) {
                return UploadEncryptedLogError.InvalidRequest
            } else if (errorType == TOO_MANY_REQUESTS) {
                return UploadEncryptedLogError.TooManyRequests
            }
        }
        return UploadEncryptedLogError.Unknown(statusCode, errorMessage)
    }

    companion object {
        private val TAG = EncryptedLogRestClient::class.java.simpleName
    }
}

internal sealed class UploadEncryptedLogResult {
    object LogUploaded : UploadEncryptedLogResult()
    class LogUploadFailed(val error: UploadEncryptedLogError) : UploadEncryptedLogResult()
}
