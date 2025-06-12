package com.automattic.encryptedlogging.utils

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object DateTimeUtils {
    // See http://drdobbs.com/java/184405382
    private val ISO8601_FORMAT: ThreadLocal<DateFormat> = object : ThreadLocal<DateFormat>() {
        override fun initialValue(): DateFormat {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        }
    }

    /**
     * Given an ISO 8601-formatted date as a String, returns a [Date] in UTC.
     */
    fun dateUTCFromIso8601(iso8601date: String): Date? {
        try {
            val replacedIso8601date = iso8601date.replace("Z", "+0000").replace("+00:00", "+0000")
            val formatter = ISO8601_FORMAT.get()
            formatter?.timeZone = TimeZone.getTimeZone("UTC")
            return formatter?.parse(replacedIso8601date)
        } catch (e: ParseException) {
            return null
        }
    }

    /**
     * Given a [Date], returns an ISO 8601-formatted String in UTC.
     */
    fun iso8601UTCFromDate(date: Date): String? {
        val tz = TimeZone.getTimeZone("UTC")
        val formatter = ISO8601_FORMAT.get()
        formatter?.timeZone = tz

        val iso8601date = formatter?.format(date)

        // Use "+00:00" notation rather than "+0000" to be consistent with the WP.COM API
        return iso8601date?.replace("+0000", "+00:00")
    }
}
