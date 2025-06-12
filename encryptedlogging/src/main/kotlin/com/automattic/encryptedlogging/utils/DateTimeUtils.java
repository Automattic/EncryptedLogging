package com.automattic.encryptedlogging.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeUtils {
    private DateTimeUtils() {
        throw new AssertionError();
    }

    // See http://drdobbs.com/java/184405382
    private static final ThreadLocal<DateFormat> ISO8601_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        }
    };

    /**
     * Given an ISO 8601-formatted date as a String, returns a {@link Date} in UTC.
     */
    public static Date dateUTCFromIso8601(String iso8601date) {
        try {
            iso8601date = iso8601date.replace("Z", "+0000").replace("+00:00", "+0000");
            DateFormat formatter = ISO8601_FORMAT.get();
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            return formatter.parse(iso8601date);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Given a {@link Date}, returns an ISO 8601-formatted String in UTC.
     */
    public static String iso8601UTCFromDate(Date date) {
        if (date == null) {
            return "";
        }
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat formatter = ISO8601_FORMAT.get();
        formatter.setTimeZone(tz);

        String iso8601date = formatter.format(date);

        // Use "+00:00" notation rather than "+0000" to be consistent with the WP.COM API
        return iso8601date.replace("+0000", "+00:00");
    }
}
