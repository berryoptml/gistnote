package dev.gistnote.app.data;

import java.util.ArrayList;
import java.util.List;

public final class LineDiff {
    private static final long MAX_LCS_CELLS = 2_000_000L;

    private LineDiff() { }

    public static List<Row> between(String before, String after) {
        String[] oldLines = lines(before);
        String[] newLines = lines(after);
        if ((long) oldLines.length * (long) newLines.length > MAX_LCS_CELLS) {
            return boundedFallback(oldLines, newLines);
        }
        int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
        for (int oldIndex = oldLines.length - 1; oldIndex >= 0; oldIndex--) {
            for (int newIndex = newLines.length - 1; newIndex >= 0; newIndex--) {
                if (oldLines[oldIndex].equals(newLines[newIndex])) {
                    lcs[oldIndex][newIndex] = 1 + lcs[oldIndex + 1][newIndex + 1];
                } else {
                    lcs[oldIndex][newIndex] = Math.max(
                            lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1]);
                }
            }
        }

        List<Row> result = new ArrayList<>();
        int oldIndex = 0;
        int newIndex = 0;
        while (oldIndex < oldLines.length || newIndex < newLines.length) {
            if (oldIndex < oldLines.length && newIndex < newLines.length
                    && oldLines[oldIndex].equals(newLines[newIndex])) {
                result.add(Row.context(oldIndex + 1, newIndex + 1, oldLines[oldIndex]));
                oldIndex++;
                newIndex++;
            } else if (oldIndex < oldLines.length && (newIndex == newLines.length
                    || lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1])) {
                result.add(Row.deleted(oldIndex + 1, oldLines[oldIndex]));
                oldIndex++;
            } else {
                result.add(Row.added(newIndex + 1, newLines[newIndex]));
                newIndex++;
            }
        }
        return result;
    }

    private static List<Row> boundedFallback(String[] oldLines, String[] newLines) {
        int prefix = 0;
        while (prefix < oldLines.length && prefix < newLines.length
                && oldLines[prefix].equals(newLines[prefix])) prefix++;
        int suffix = 0;
        while (suffix < oldLines.length - prefix && suffix < newLines.length - prefix
                && oldLines[oldLines.length - suffix - 1]
                .equals(newLines[newLines.length - suffix - 1])) suffix++;

        List<Row> result = new ArrayList<>();
        for (int i = 0; i < prefix; i++) result.add(Row.context(i + 1, i + 1, oldLines[i]));
        for (int i = prefix; i < oldLines.length - suffix; i++) {
            result.add(Row.deleted(i + 1, oldLines[i]));
        }
        for (int i = prefix; i < newLines.length - suffix; i++) {
            result.add(Row.added(i + 1, newLines[i]));
        }
        for (int i = 0; i < suffix; i++) {
            int oldIndex = oldLines.length - suffix + i;
            int newIndex = newLines.length - suffix + i;
            result.add(Row.context(oldIndex + 1, newIndex + 1, oldLines[oldIndex]));
        }
        return result;
    }

    private static String[] lines(String value) {
        if (value == null || value.isEmpty()) return new String[0];
        return value.split("\\n", -1);
    }

    public enum Kind { CONTEXT, ADDED, DELETED }

    public static final class Row {
        public final Kind kind;
        public final int oldLine;
        public final int newLine;
        public final String text;

        private Row(Kind kind, int oldLine, int newLine, String text) {
            this.kind = kind;
            this.oldLine = oldLine;
            this.newLine = newLine;
            this.text = text;
        }

        static Row context(int oldLine, int newLine, String text) {
            return new Row(Kind.CONTEXT, oldLine, newLine, text);
        }

        static Row added(int newLine, String text) {
            return new Row(Kind.ADDED, 0, newLine, text);
        }

        static Row deleted(int oldLine, String text) {
            return new Row(Kind.DELETED, oldLine, 0, text);
        }
    }
}

