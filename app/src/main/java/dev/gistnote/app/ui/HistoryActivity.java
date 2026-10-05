package dev.gistnote.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.GistRevision;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.LineDiff;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.data.SyncEngine;
import dev.gistnote.app.security.TokenStore;
import dev.gistnote.app.widget.NoteWidgetProvider;

public final class HistoryActivity extends Activity {
    public static final String EXTRA_GIST_ID = "gist_id";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_TITLE = "title";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String gistId;
    private String filename;
    private TextView status;
    private RevisionAdapter adapter;
    private GitHubApi api;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_history);
        SystemBars.apply(findViewById(R.id.history_root));
        gistId = getIntent().getStringExtra(EXTRA_GIST_ID);
        filename = getIntent().getStringExtra(EXTRA_FILENAME);
        if (gistId == null || filename == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.history_title)).setText(
                getIntent().getStringExtra(EXTRA_TITLE));
        status = findViewById(R.id.history_status);
        ListView list = findViewById(R.id.history_list);
        adapter = new RevisionAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                preview(adapter.getItem(position), adapter.parentOf(position)));
        api = new GitHubApi(new TokenStore(this).getToken());
        load();
    }

    private void load() {
        status.setText("Loading revisions from GitHub…");
        executor.execute(() -> {
            try {
                List<GistRevision> revisions = api.listRevisions(gistId);
                runOnUiThread(() -> {
                    adapter.setItems(revisions);
                    status.setText(revisions.size() + " revision" + (revisions.size() == 1 ? "" : "s")
                            + " · tap one to inspect or restore");
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.getMessage()));
            }
        });
    }

    private void preview(GistRevision revision, GistRevision parent) {
        status.setText("Building diff for " + revision.shortVersion() + "…");
        executor.execute(() -> {
            try {
                GistNote selected = api.getRevision(gistId, revision.version, filename);
                String before = "";
                if (parent != null) {
                    before = api.getRevision(gistId, parent.version, filename).content;
                }
                List<LineDiff.Row> diff = LineDiff.between(before, selected.content);
                String comparison = parent == null
                        ? "Initial revision"
                        : "Compared with " + parent.shortVersion();
                runOnUiThread(() -> {
                    status.setText("Revision " + revision.shortVersion());
                    RevisionDiffDialog.show(this,
                            revision.committedAt.replace('T', ' ').replace("Z", " UTC"),
                            comparison, diff, () -> restore(selected.content));
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.getMessage()));
            }
        });
    }

    private void restore(String content) {
        NoteStore.CachedNote local = new NoteStore(this).getNote(gistId);
        if (local != null && local.dirty) {
            status.setText("Resolve or sync the on-device draft before restoring history.");
            return;
        }
        status.setText("Restoring as a new GitHub revision…");
        executor.execute(() -> {
            try {
                GistNote latest = api.getGist(gistId, filename);
                NoteStore store = new NoteStore(this);
                NoteStore.CachedNote base = new NoteStore.CachedNote(
                        latest.id, latest.title, latest.filename, latest.content,
                        latest.updatedAt, latest.version, false);
                GistNote restored = new SyncEngine(api, store).save(base, content);
                runOnUiThread(() -> {
                    status.setText("Restored. GitHub kept the previous versions.");
                    NoteWidgetProvider.updateWidgetsForGist(this, gistId);
                    load();
                });
            } catch (SyncEngine.ConflictException conflict) {
                runOnUiThread(() -> status.setText(
                        "The note changed again. Reopen the revision before restoring."));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.getMessage()));
            }
        });
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private final class RevisionAdapter extends BaseAdapter {
        private final List<GistRevision> items = new ArrayList<>();

        void setItems(List<GistRevision> revisions) {
            items.clear();
            items.addAll(revisions);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public GistRevision getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        GistRevision parentOf(int position) {
            return position + 1 < items.size() ? items.get(position + 1) : null;
        }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            View view = recycled == null ? LayoutInflater.from(HistoryActivity.this)
                    .inflate(android.R.layout.simple_list_item_2, parent, false) : recycled;
            GistRevision revision = getItem(position);
            TextView first = view.findViewById(android.R.id.text1);
            TextView second = view.findViewById(android.R.id.text2);
            first.setText(revision.committedAt.replace('T', ' ').replace("Z", " UTC"));
            first.setTextColor(getColor(R.color.ink));
            second.setText(revision.shortVersion() + "  ·  +" + revision.additions
                    + " / −" + revision.deletions);
            second.setTextColor(getColor(R.color.muted));
            return view;
        }
    }
}
