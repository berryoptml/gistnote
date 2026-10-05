package dev.gistnote.app.ui;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class SyncTime {
    private SyncTime() { }

    public static String label(Context context, long syncedAt) {
        if (syncedAt <= 0L) return "Not synced yet";
        String time = DateFormat.getTimeFormat(context).format(new Date(syncedAt));
        if (DateUtils.isToday(syncedAt)) return "Synced " + time;
        String date = DateFormat.getMediumDateFormat(context).format(new Date(syncedAt));
        return "Synced " + date + ", " + time;
    }

    public static String shortLabel(Context context, long syncedAt) {
        if (syncedAt <= 0L) return "not synced";
        return DateFormat.getTimeFormat(context).format(new Date(syncedAt));
    }

    public static String formatDateTime(long syncedAt, String updatedAt) {
        long time = syncedAt > 0L ? syncedAt : parseIso(updatedAt);
        if (time <= 0L) time = System.currentTimeMillis();
        return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                .format(new Date(time));
    }

    public static String whatsappStatus(boolean dirty, long syncedAt, String updatedAt) {
        return formatDateTime(syncedAt, updatedAt);
    }

    private static long parseIso(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            source.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = source.parse(value);
            return date != null ? date.getTime() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
