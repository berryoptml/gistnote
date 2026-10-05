package dev.gistnote.app.debug;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.GridView;

import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.ui.CreateGistActivity;
import dev.gistnote.app.ui.HistoryActivity;
import dev.gistnote.app.ui.MainActivity;
import dev.gistnote.app.ui.NoteActivity;
import dev.gistnote.app.ui.SettingsActivity;
import dev.gistnote.app.ui.DebugPreviewActivity;
import dev.gistnote.app.ui.GistAdapter;
import dev.gistnote.app.widget.StackWidgetConfigActivity;
import dev.gistnote.app.widget.WidgetConfigActivity;

/**
 * Debug-only adb entry point for screenshot capture. It seeds only local,
 * synthetic cache data and never writes a token or calls the network.
 *
 * Example: adb shell am start -n dev.gistnote.app/.debug.ScreenshotHarnessActivity \
 *   --es mode widget
 */
public final class ScreenshotHarnessActivity extends Activity {
    private static final String GIST_ID = "debug-gistnote-fixture";
    private static final int WIDGET_ID = 42;
    private static final int STACK_WIDGET_ID = 43;
    private Application.ActivityLifecycleCallbacks homeFixtures;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode");
        if (mode == null || mode.isEmpty()) mode = "home";
        seedFixtures();
        if ("pin-widget".equals(mode)) {
            dev.gistnote.app.widget.WidgetPinning.request(this, homeNotes().get(0));
            return;
        }
        if ("widget-choices".equals(mode)) {
            dev.gistnote.app.widget.WidgetPinning.showChoices(this, homeNotes().get(0));
            return;
        }
        if (mode.startsWith("home")) {
            boolean grid = "home-grid".equals(mode);
            getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("grid_mode", grid).apply();
            installHomeFixtures();
        }

        Intent destination;
        switch (mode) {
            case "editor":
                destination = noteIntent(false);
                break;
            case "widget":
            case "widget-modal":
                destination = noteIntent(true);
                break;
            case "widget-settings":
                destination = new Intent(this, dev.gistnote.app.widget.WidgetAppearanceActivity.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID);
                break;
            case "history":
                destination = new Intent(this, HistoryActivity.class)
                        .putExtra(HistoryActivity.EXTRA_GIST_ID, GIST_ID)
                        .putExtra(HistoryActivity.EXTRA_FILENAME, "project-notes.md")
                        .putExtra(HistoryActivity.EXTRA_TITLE, "Research scratchpad");
                break;
            case "create":
                destination = new Intent(this, CreateGistActivity.class);
                break;
            case "settings":
                destination = new Intent(this, SettingsActivity.class);
                break;
            case "widget-config":
                destination = configIntent(WidgetConfigActivity.class, WIDGET_ID + 1000);
                break;
            case "stack-config":
            case "stack-widget-config":
                destination = configIntent(StackWidgetConfigActivity.class, STACK_WIDGET_ID);
                break;
            case "diff":
            case "picker":
                destination = new Intent(this, DebugPreviewActivity.class).putExtra("mode", mode);
                break;
            case "home":
            default:
                destination = new Intent(this, MainActivity.class);
                break;
        }
        startActivity(destination);
        finish();
    }

    private void installHomeFixtures() {
        // MainActivity normally clears its adapter when no token is present. Inject
        // after its onResume so the home screenshot is useful without credentials.
        homeFixtures = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                if (!(activity instanceof MainActivity)) return;
                GridView grid = activity.findViewById(dev.gistnote.app.R.id.gist_list);
                if (grid != null && grid.getAdapter() instanceof GistAdapter) {
                    grid.postDelayed(() -> {
                        if (grid.getAdapter() instanceof GistAdapter) {
                            ((GistAdapter) grid.getAdapter()).setNotes(homeNotes());
                            android.widget.TextView status = activity.findViewById(dev.gistnote.app.R.id.status_text);
                            if (status != null) status.setVisibility(android.view.View.GONE);
                        }
                    }, 300L);
                }
                getApplication().unregisterActivityLifecycleCallbacks(this);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        };
        getApplication().registerActivityLifecycleCallbacks(homeFixtures);
    }

    private static java.util.List<GistNote> homeNotes() {
        java.util.ArrayList<GistNote> notes = new java.util.ArrayList<>();
        notes.add(new GistNote(GIST_ID, "Research scratchpad", "project-notes.md",
                "- [x] Update the model notes\n- [ ] Check the latest experiment\n  - [ ] Compare baselines", "2026-09-18T09:30:00Z",
                "debug-version", false, false, ""));
        notes.add(new GistNote("debug-reading-list", "Reading list", "reading-list.md",
                "Papers to revisit this week.\n\n1. Proximal methods\n2. Incentive design\n3. Numerical validation", "2026-09-17T16:10:00Z",
                "debug-version-2", true, false, ""));
        notes.add(new GistNote("debug-weekly-plan", "Weekly plan", "plan.md",
                "One focused improvement at a time.\n\n- [ ] Write the introduction\n- [ ] Review experiments", "2026-09-16T08:00:00Z",
                "debug-version-3", false, false, ""));
        return notes;
    }

    private Intent noteIntent(boolean widgetModal) {
        Intent intent = new Intent(this, NoteActivity.class)
                .putExtra(NoteActivity.EXTRA_GIST_ID, GIST_ID)
                .putExtra(NoteActivity.EXTRA_FILENAME, "project-notes.md")
                .putExtra(NoteActivity.EXTRA_TITLE, "Research scratchpad");
        if (widgetModal) intent.putExtra(NoteActivity.EXTRA_WIDGET_ID, WIDGET_ID);
        return intent;
    }

    private Intent configIntent(Class<? extends Activity> activity, int widgetId) {
        return new Intent(this, activity)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    }

    private void seedFixtures() {
        NoteStore store = new NoteStore(this);
        GistNote note = new GistNote(
                GIST_ID,
                "Research scratchpad",
                "project-notes.md",
                "# Research scratchpad\n\n"
                        + "A calm place for ideas, experiments, and next steps.\n\n"
                        + "- [ ] Check the latest experiment\n"
                        + "- [x] Update the model notes\n\n"
                        + "## Today\n\nKeep the small details visible.",
                "2026-09-18T09:30:00Z", "debug-version", false, false, "");
        store.putRemote(note);
        store.bindWidget(WIDGET_ID, new NoteStore.WidgetBinding(
                GIST_ID, note.filename, "Peach", "Brown", "Medium"));
        store.bindStack(STACK_WIDGET_ID, new NoteStore.StackWidgetBinding(
                java.util.Collections.singletonList(new NoteStore.StackNote(
                        GIST_ID, note.filename, "Mint", "Brown", "Medium")),
                "Transparent", "White", "Medium"));
    }
}
