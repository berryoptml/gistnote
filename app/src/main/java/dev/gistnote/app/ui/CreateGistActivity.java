package dev.gistnote.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.MarkdownTasks;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.security.TokenStore;

public final class CreateGistActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_create_gist);
        SystemBars.apply(findViewById(R.id.create_root));
        EditText description = findViewById(R.id.description_input);
        EditText filename = findViewById(R.id.filename_input);
        EditText content = findViewById(R.id.content_input);
        TextView status = findViewById(R.id.create_status);
        Button createLocal = findViewById(R.id.create_local_button);
        Button createGist = findViewById(R.id.create_gist_button);
        configureEditor(content);
        findViewById(R.id.create_add_task_button).setOnClickListener(v -> applyEdit(content,
                MarkdownTasks.insertTask(content.getText().toString(), content.getSelectionStart())));
        findViewById(R.id.create_indent_button).setOnClickListener(v -> applyEdit(content,
                MarkdownTasks.changeIndent(content.getText().toString(), content.getSelectionStart(), true)));
        findViewById(R.id.create_outdent_button).setOnClickListener(v -> applyEdit(content,
                MarkdownTasks.changeIndent(content.getText().toString(), content.getSelectionStart(), false)));
        TooltipHelper.applyTooltips(findViewById(R.id.create_root));
        findViewById(R.id.cancel_button).setOnClickListener(v -> finish());
        createLocal.setOnClickListener(v -> {
            Values values = values(description, filename, content);
            NoteStore store = new NoteStore(this);
            GistNote note = store.createLocalNote(values.title, values.filename, values.content);
            open(note);
        });
        createGist.setEnabled(new TokenStore(this).hasToken());
        if (!createGist.isEnabled()) {
            createGist.setContentDescription("Create gist unavailable until a GitHub token is added in settings");
        }
        createGist.setOnClickListener(v -> {
            Values values = values(description, filename, content);
            final String finalTitle = values.title;
            final String finalFilename = values.filename;
            final String finalContent = values.content;
            createGist.setEnabled(false);
            createLocal.setEnabled(false);
            status.setText("Creating on GitHub…");
            executor.execute(() -> {
                try {
                    GistNote note = new GitHubApi(new TokenStore(this).getToken())
                            .createGist(finalTitle, finalFilename, finalContent);
                    NoteStore store = new NoteStore(this);
                    store.putRemote(note);
                    store.prependGist(note);
                    runOnUiThread(() -> open(note));
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        createGist.setEnabled(true);
                        createLocal.setEnabled(true);
                        status.setText(error.getMessage());
                    });
                }
            });
        });
    }

    private void configureEditor(EditText editor) {
        editor.addTextChangedListener(new TextWatcher() {
            private boolean formatting;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override public void afterTextChanged(Editable s) {
                if (formatting) return;
                formatting = true;
                try {
                    MarkdownStyler.format(s, getColor(R.color.ink), getColor(R.color.muted),
                            editor.getTextSize(), getResources().getDisplayMetrics().density);
                } finally {
                    formatting = false;
                }
            }
        });
    }

    private void applyEdit(EditText editor, MarkdownTasks.Edit edit) {
        editor.setText(edit.content);
        editor.setSelection(Math.min(edit.cursor, editor.length()));
        editor.requestFocus();
    }

    private Values values(EditText description, EditText filename, EditText content) {
        String titleValue = description.getText().toString().trim();
        String filenameValue = filename.getText().toString().trim();
        if (titleValue.isEmpty()) titleValue = "Untitled note";
        if (filenameValue.isEmpty()) filenameValue = "note.md";
        if (!filenameValue.contains(".")) filenameValue += ".md";
        return new Values(titleValue, filenameValue, content.getText().toString());
    }

    private void open(GistNote note) {
        Intent open = new Intent(this, NoteActivity.class)
                .putExtra(NoteActivity.EXTRA_GIST_ID, note.id)
                .putExtra(NoteActivity.EXTRA_FILENAME, note.filename)
                .putExtra(NoteActivity.EXTRA_TITLE, note.title);
        startActivity(open);
        finish();
    }

    private static final class Values {
        final String title;
        final String filename;
        final String content;

        Values(String title, String filename, String content) {
            this.title = title;
            this.filename = filename;
            this.content = content;
        }
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
