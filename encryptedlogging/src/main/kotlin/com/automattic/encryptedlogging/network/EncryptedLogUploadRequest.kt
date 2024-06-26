package com.automattic.encryptedlogging.network

import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.Response.ErrorListener
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import org.json.JSONObject

private const val AUTHORIZATION_HEADER = "Authorization"
private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val CONTENT_TYPE_JSON = "application/json"
private const val UUID_HEADER = "log-uuid"

internal class EncryptedLogUploadRequest(
    private val logUuid: String,
    private val contents: String,
    private val clientSecret: String,
    private val successListener: Response.Listener<NetworkResponse>,
    errorListener: ErrorListener
) : Request<NetworkResponse>(Method.POST, "https://public-api.wordpress.com/rest/v1.1/encrypted-logging/", errorListener) {
    override fun getHeaders(): Map<String, String> {
        return mapOf(
                CONTENT_TYPE_HEADER to CONTENT_TYPE_JSON,
                AUTHORIZATION_HEADER to clientSecret,
                UUID_HEADER to logUuid
        )
    }

    @Suppress("ForbiddenComment")
    override fun getBody(): ByteArray {
        // TODO: Max file size is 10MB - maybe we should just handle that in the error callback?
        return contents.toByteArray()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun parseNetworkResponse(response: NetworkResponse?): Response<NetworkResponse> {
        return try {
            Response.success(response, HttpHeaderParser.parseCacheHeaders(response))
        } catch (e: Exception) {
            try {
                val json = JSONObject(response.toString())
                val errorMessage = json.getString("message")
                Response.error(VolleyError(errorMessage))
            } catch (jsonParsingError: Throwable) {
                Response.error(ParseError(jsonParsingError))
            }
        }
    }

    override fun deliverResponse(response: NetworkResponse) {
        successListener.onResponse(response)
    }
}
