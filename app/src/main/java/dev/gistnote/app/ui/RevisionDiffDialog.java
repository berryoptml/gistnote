package dev.gistnote.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import dev.gistnote.app.R;
import dev.gistnote.app.data.LineDiff;

final class RevisionDiffDialog {
    private RevisionDiffDialog() { }

    static void show(Activity activity, String title, String subtitle,
                     List<LineDiff.Row> rows, Runnable restore) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(activity.getColor(R.color.ink));
        heading.setTextSize(18);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(heading);

        TextView comparison = new TextView(activity);
        comparison.setText(subtitle);
        comparison.setTextColor(activity.getColor(R.color.muted));
        comparison.setTextSize(12);
        comparison.setPadding(0, dp(activity, 2), 0, dp(activity, 10));
        content.addView(comparison);

        LinearLayout diff = new LinearLayout(activity);
        diff.setOrientation(LinearLayout.VERTICAL);
        for (LineDiff.Row row : rows) diff.addView(row(activity, row));
        if (rows.isEmpty()) {
            TextView unchanged = new TextView(activity);
            unchanged.setText("No textual changes");
            unchanged.setTextColor(activity.getColor(R.color.muted));
            unchanged.setPadding(dp(activity, 8), dp(activity, 12), dp(activity, 8), dp(activity, 12));
            diff.addView(unchanged);
        }

        HorizontalScrollView horizontal = new HorizontalScrollView(activity);
        horizontal.setFillViewport(true);
        horizontal.addView(diff, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView vertical = new ScrollView(activity);
        vertical.addView(horizontal, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(vertical, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, Math.min(440, Math.max(80, rows.size() * 25 + 16)))));

        new AlertDialog.Builder(activity)
                .setView(content)
                .setNegativeButton("Close", null)
                .setPositiveButton("Restore as latest", (dialog, which) -> restore.run())
                .show();
    }

    private static TextView row(Activity activity, LineDiff.Row row) {
        String oldNumber = row.oldLine == 0 ? "" : Integer.toString(row.oldLine);
        String newNumber = row.newLine == 0 ? "" : Integer.toString(row.newLine);
        String marker;
        int background;
        boolean dark = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (row.kind == LineDiff.Kind.ADDED) {
            marker = "+";
            background = dark ? 0xFF173B2D : Color.rgb(220, 246, 225);
        } else if (row.kind == LineDiff.Kind.DELETED) {
            marker = "-";
            background = dark ? 0xFF48262B : Color.rgb(253, 224, 224);
        } else {
            marker = " ";
            background = activity.getColor(R.color.surface);
        }
        TextView view = new TextView(activity);
        view.setText(String.format(Locale.ROOT, "%5s %5s %s %s",
                oldNumber, newNumber, marker, row.text));
        view.setTextColor(activity.getColor(R.color.ink));
        view.setTextSize(13);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setSingleLine(true);
        view.setBackgroundColor(background);
        view.setPadding(dp(activity, 6), dp(activity, 3), dp(activity, 10), dp(activity, 3));
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
