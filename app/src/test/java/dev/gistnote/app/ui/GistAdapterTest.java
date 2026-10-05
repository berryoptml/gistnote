package dev.gistnote.app.ui;

import org.junit.Test;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.assertEquals;

public class GistAdapterTest {
    @Test public void timestampsUseDeviceTimeZone() {
        TimeZone originalZone = TimeZone.getDefault();
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            assertEquals("Edited Sep 18 · 3:00 PM", GistAdapter.compactDate("2026-09-18T09:30:00Z"));
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertEquals("Edited Sep 18 · 5:30 AM", GistAdapter.compactDate("2026-09-18T09:30:00Z"));
        } finally {
            TimeZone.setDefault(originalZone);
            Locale.setDefault(originalLocale);
        }
    }

    @Test public void previewPreservesLinesAndNestedTasks() {
        assertEquals("☑ Done\n  ☐ Nested\nParagraph", GistAdapter.preview("- [x] Done\n  - [ ] Nested\nParagraph"));
    }

    @Test public void absentContentAndTimestampAreSafe() {
        assertEquals("Empty note", GistAdapter.preview(null));
        assertEquals("not synced", GistAdapter.compactDate(""));
        assertEquals("Edited time unavailable", GistAdapter.compactDate("invalid"));
    }
}
