package dev.gistnote.app.ui;

import java.util.ArrayList;
import java.util.List;

public final class DiffHelper {
    public enum DiffType { SAME, ADD, DELETE }

    public static final class DiffLine {
        public final DiffType type;
        public final String text;

        public DiffLine(DiffType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private DiffHelper() { }

    /**
     * Computes line-by-line diff between remoteText (base) and localText (modified).
     */
    public static List<DiffLine> compute(String remoteText, String localText) {
        String[] oldLines = remoteText == null || remoteText.isEmpty() ? new String[0] : remoteText.split("\n", -1);
        String[] newLines = localText == null || localText.isEmpty() ? new String[0] : localText.split("\n", -1);
        int n = oldLines.length;
        int m = newLines.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (oldLines[i].equals(newLines[j])) {
                    dp[i + 1][j + 1] = dp[i][j] + 1;
                } else {
                    dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        List<DiffLine> reverse = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                reverse.add(new DiffLine(DiffType.SAME, oldLines[i - 1]));
                i--; j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                reverse.add(new DiffLine(DiffType.ADD, newLines[j - 1]));
                j--;
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                reverse.add(new DiffLine(DiffType.DELETE, oldLines[i - 1]));
                i--;
            }
        }
        List<DiffLine> result = new ArrayList<>(reverse.size());
        for (int k = reverse.size() - 1; k >= 0; k--) {
            result.add(reverse.get(k));
        }
        return result;
    }
}
