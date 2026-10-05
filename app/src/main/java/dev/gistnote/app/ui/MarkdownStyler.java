package dev.gistnote.app.ui;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownStyler {
    private static final Pattern TASK_PATTERN = Pattern.compile("^(\\s*)([-*+]\\s+\\[([ xX])\\]\\s?)(.*)$");
    private static final Pattern H1_PATTERN = Pattern.compile("^(#\\s+)(.*)$");
    private static final Pattern H2_PATTERN = Pattern.compile("^(##\\s+)(.*)$");

    private MarkdownStyler() { }

    public static void format(Spannable spannable, int textColor, int mutedColor, float textSizePx, float density) {
        if (spannable == null || spannable.length() == 0) return;

        // Clear previous formatting spans
        for (TaskCheckboxSpan span : spannable.getSpans(0, spannable.length(), TaskCheckboxSpan.class)) {
            spannable.removeSpan(span);
        }
        for (RelativeSizeSpan span : spannable.getSpans(0, spannable.length(), RelativeSizeSpan.class)) {
            spannable.removeSpan(span);
        }
        for (StyleSpan span : spannable.getSpans(0, spannable.length(), StyleSpan.class)) {
            spannable.removeSpan(span);
        }
        for (StrikethroughSpan span : spannable.getSpans(0, spannable.length(), StrikethroughSpan.class)) {
            spannable.removeSpan(span);
        }
        for (ForegroundColorSpan span : spannable.getSpans(0, spannable.length(), ForegroundColorSpan.class)) {
            spannable.removeSpan(span);
        }

        int boxSize = Math.round(textSizePx * 0.96f);
        int marginEnd = Math.round(7f * density);

        String text = spannable.toString();
        int len = text.length();
        int lineStart = 0;
        int lineIndex = 0;

        while (lineStart <= len) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd == -1) lineEnd = len;

            String line = text.substring(lineStart, lineEnd);
            Matcher taskMatcher = TASK_PATTERN.matcher(line);
            if (taskMatcher.matches()) {
                String indent = taskMatcher.group(1);
                String marker = taskMatcher.group(2);
                boolean checked = taskMatcher.group(3).equalsIgnoreCase("x");

                int markerStart = lineStart + indent.length();
                int markerEnd = markerStart + marker.length();

                spannable.setSpan(
                        new TaskCheckboxSpan(checked, boxSize, marginEnd, checked ? mutedColor : textColor, lineIndex),
                        markerStart,
                        markerEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                if (checked && markerEnd < lineEnd) {
                    spannable.setSpan(
                            new StrikethroughSpan(),
                            markerEnd,
                            lineEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    spannable.setSpan(
                            new ForegroundColorSpan(mutedColor),
                            markerEnd,
                            lineEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            } else {
                Matcher h1 = H1_PATTERN.matcher(line);
                if (h1.matches()) {
                    spannable.setSpan(new RelativeSizeSpan(1.25f), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new ForegroundColorSpan(mutedColor), lineStart, lineStart + h1.group(1).length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    Matcher h2 = H2_PATTERN.matcher(line);
                    if (h2.matches()) {
                        spannable.setSpan(new RelativeSizeSpan(1.12f), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        spannable.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        spannable.setSpan(new ForegroundColorSpan(mutedColor), lineStart, lineStart + h2.group(1).length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }

            lineStart = lineEnd + 1;
            lineIndex++;
        }
    }
}
