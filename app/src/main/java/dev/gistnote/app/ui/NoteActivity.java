package dev.gistnote.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.MarkdownTasks;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.data.SyncEngine;
import dev.gistnote.app.security.TokenStore;
import dev.gistnote.app.widget.NoteWidgetProvider;
import dev.gistnote.app.widget.StackNoteWidgetProvider;

public final class NoteActivity extends Activity {
    public static final String EXTRA_GIST_ID = "gist_id";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_WIDGET_ID = "widget_id";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NoteStore store;
    private SyncEngine syncEngine;
    private NoteStore.CachedNote current;
    private NoteStore.WidgetBinding appearance;
    private String gistId;
    private String filename;
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ImageView syncTickIcon;
    private TextView syncStatus;
    private View noteRoot;
    private View noteSheet;
    private View noteHeader;
    private View noteToolbar;
    private TextView readText;
    private TextView noteHeaderTitle;
    private TextView noteHeaderSubtitle;
    private View headerBackButton;
    private View headerDoneButton;
    private View headerCloseButton;
    private View modalDragHandle;
    private ScrollView readPanel;
    private LinearLayout editPanel;
    private EditText editor;
    private View commitButton;
    private View closeButton;
    private View readActionRow;
    private View editActionRow;
    private boolean saving;
    private boolean stackAppearance;
    private boolean localNote;
    private String readDraft = "";
    private boolean readDraftDirty;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        gistId = getIntent().getStringExtra(EXTRA_GIST_ID);
        filename = getIntent().getStringExtra(EXTRA_FILENAME);
        widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);

        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().setDimAmount(0.4f);
        if (Build.VERSION.SDK_INT >= 31) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            try {
                int blurRadius = getSharedPreferences("widget_style", MODE_PRIVATE).getInt("panel_blur", 36);
                if (blurRadius <= 0) blurRadius = 36;
                getWindow().setBackgroundBlurRadius(dp(blurRadius));
            } catch (Exception ignored) { }
        }
        setContentView(R.layout.activity_note);
        noteRoot = findViewById(R.id.note_root);
        noteSheet = findViewById(R.id.note_sheet);
        noteHeader = findViewById(R.id.note_header);
        noteToolbar = findViewById(R.id.note_toolbar);
        noteRoot.setOnClickListener(v -> exitOverlay());
        noteSheet.setOnClickListener(v -> {});
        SystemBars.applyWithIme(noteRoot);
        String initialTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (gistId == null || gistId.isEmpty()) {
            finish();
            return;
        }
        localNote = NoteStore.isLocalId(gistId);
        if (filename == null || filename.isEmpty()) filename = "note.md";

        store = new NoteStore(this);
        syncEngine = new SyncEngine(new GitHubApi(new TokenStore(this).getToken()), store);
        syncStatus = findViewById(R.id.sync_status);
        readText = findViewById(R.id.read_text);
        noteHeaderTitle = findViewById(R.id.note_header_title);
        noteHeaderSubtitle = findViewById(R.id.note_header_subtitle);
        headerBackButton = findViewById(R.id.header_back_button);
        if (headerBackButton != null) {
            headerBackButton.setOnClickListener(v -> onBackPressed());
        }
        headerDoneButton = findViewById(R.id.header_done_button);
        headerCloseButton = findViewById(R.id.header_close_button);
        if (headerCloseButton != null) {
            headerCloseButton.setOnClickListener(v -> {
                if (editPanel.getVisibility() == View.VISIBLE) {
                    showReading();
                } else {
                    exitOverlay();
                }
            });
        }
        modalDragHandle = findViewById(R.id.modal_drag_handle);
        readPanel = findViewById(R.id.read_panel);
        editPanel = findViewById(R.id.edit_panel);
        editor = findViewById(R.id.note_editor);
        commitButton = findViewById(R.id.save_note_button);
        closeButton = findViewById(R.id.close_button);
        readActionRow = findViewById(R.id.read_action_row);
        editActionRow = findViewById(R.id.edit_action_row);
        syncTickIcon = findViewById(R.id.sync_tick_icon);

        current = store.getNote(gistId);
        if (current == null) {
            current = new NoteStore.CachedNote(gistId,
                    TextUtils.isEmpty(initialTitle) ? filename : initialTitle,
                    filename, "", "", "", false);
        }
        String displayTitle = current.title != null && !current.title.isEmpty() ? current.title : current.filename;
        noteHeaderTitle.setText(displayTitle);
        if (noteHeaderSubtitle != null) {
            noteHeaderSubtitle.setText(current.filename
                    + (localNote ? " · Local · Preview" : " · Preview"));
        }
        headerDoneButton.setOnClickListener(v -> {
            if (editPanel.getVisibility() == View.VISIBLE) {
                save(editor.getText().toString(), true);
            } else {
                if (readDraft.equals(current.content)) exitOverlay();
                else save(readDraft, false);
            }
        });

        initAppearance();
        applyAppearance();
        updateSyncStatus(current.dirty, current.syncedAt, null);
        render(current.content);

        readText.setOnTouchListener((v, event) -> {
            if (handleCheckboxTouch(readText, event, false)) {
                return true;
            }
            return false;
        });

        editor.addTextChangedListener(new TextWatcher() {
            private boolean formatting = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (formatting) return;
                formatting = true;
                try {
                    MarkdownStyler.format(s, bodyTextColor(), mutedTextColor(), editor.getTextSize(),
                            getResources().getDisplayMetrics().density);
                } finally {
                    formatting = false;
                }
            }
        });

        editor.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                int start = editor.getSelectionStart();
                int end = editor.getSelectionEnd();
                if (start == end && start > 0) {
                    Editable s = editor.getText();
                    TaskCheckboxSpan[] spans = s.getSpans(start - 1, start, TaskCheckboxSpan.class);
                    for (TaskCheckboxSpan span : spans) {
                        int spanStart = s.getSpanStart(span);
                        int spanEnd = s.getSpanEnd(span);
                        if (spanEnd == start) {
                            s.delete(spanStart, spanEnd);
                            return true;
                        }
                    }
                    String beforeCursor = s.subSequence(0, start).toString();
                    int lastNl = beforeCursor.lastIndexOf('\n');
                    String linePart = lastNl >= 0 ? beforeCursor.substring(lastNl + 1) : beforeCursor;
                    Matcher m = Pattern.compile("^(\\s*)[-*+]\\s\\[[ xX]\\]\\s?$").matcher(linePart);
                    if (m.matches()) {
                        int delStart = (lastNl >= 0 ? lastNl + 1 : 0) + m.group(1).length();
                        s.delete(delStart, start);
                        return true;
                    }
                }
            }
            return false;
        });

        editor.setOnTouchListener((v, event) -> handleCheckboxTouch(editor, event, true));

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            modalDragHandle.setVisibility(View.VISIBLE);
        } else {
            modalDragHandle.setVisibility(View.GONE);
        }
        noteRoot.post(() -> setOverlaySize(false));

        View readRefreshButton = findViewById(R.id.read_refresh_button);
        if (readRefreshButton != null) {
            if (localNote) readRefreshButton.setContentDescription("Saved on this device");
            readRefreshButton.setOnClickListener(v -> syncExplicitly());
        }
        findViewById(R.id.read_edit_button).setOnClickListener(v -> beginEditing());
        closeButton.setOnClickListener(v -> showReading());
        findViewById(R.id.read_cancel_button).setOnClickListener(v -> exitOverlay());
        findViewById(R.id.read_commit_button).setOnClickListener(v -> {
            if (readDraft.equals(current.content)) exitOverlay();
            else save(readDraft, false);
        });
        commitButton.setOnClickListener(v -> save(editor.getText().toString(), true));
        findViewById(R.id.add_task_button).setOnClickListener(v -> {
            ensureEditing();
            insertTask();
        });
        findViewById(R.id.indent_button).setOnClickListener(v -> {
            ensureEditing();
            changeIndent(true);
        });
        findViewById(R.id.outdent_button).setOnClickListener(v -> {
            ensureEditing();
            changeIndent(false);
        });
        TooltipHelper.applyTooltips(noteRoot);
    }

    @Override public void onBackPressed() {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            exitOverlay();
            return;
        }
        if (editPanel != null && editPanel.getVisibility() == View.VISIBLE) {
            showReading();
        } else {
            exitOverlay();
        }
    }

    private void exitOverlay() {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    private void updateSyncStatus(boolean dirty, long syncedAt, String customMessage) {
        if (customMessage != null) {
            if (syncTickIcon != null) syncTickIcon.setVisibility(View.GONE);
            syncStatus.setText(customMessage);
            return;
        }
        if (localNote) {
            if (syncTickIcon != null) {
                syncTickIcon.setVisibility(View.VISIBLE);
                syncTickIcon.setImageResource(R.drawable.ic_tick_single);
                syncTickIcon.setImageAlpha(180);
            }
            syncStatus.setText("Local note · saved on device");
            return;
        }
        if (syncTickIcon != null) {
            syncTickIcon.setVisibility(View.VISIBLE);
            syncTickIcon.setImageResource(dirty ? R.drawable.ic_tick_single : R.drawable.ic_tick_double);
            syncTickIcon.setImageAlpha(180);
        }
        if (dirty) {
            syncStatus.setText("Saved on device · not synced");
        } else {
            String time = SyncTime.shortLabel(this, syncedAt);
            syncStatus.setText(time.isEmpty() ? "Synced" : "Synced " + time);
        }
    }

    private void syncExplicitly() {
        if (saving) return;
        if (localNote) {
            current = store.getNote(gistId);
            updateSyncStatus(false, 0L, null);
            android.widget.Toast.makeText(this, "Saved on this device",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        saving = true;
        if (syncTickIcon != null) syncTickIcon.setVisibility(View.GONE);
        if (current.dirty) {
            syncStatus.setText("Syncing with GitHub…");
            NoteStore.CachedNote base = current;
            executor.execute(() -> {
                try {
                    GistNote saved = syncEngine.save(base, base.content);
                    runOnUiThread(() -> {
                        current = store.getNote(gistId);
                        render(saved.content);
                        saving = false;
                        updateSyncStatus(false, current.syncedAt, null);
                        NoteWidgetProvider.updateWidgetsForGist(this, gistId);
                    });
                } catch (SyncEngine.ConflictException conflict) {
                    runOnUiThread(() -> {
                        saving = false;
                        showConflictDiff(conflict);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        saving = false;
                        updateSyncStatus(true, current.syncedAt, "Sync failed · kept on device");
                    });
                }
            });
        } else {
            syncStatus.setText("Checking GitHub…");
            executor.execute(() -> {
                try {
                    GistNote remote = syncEngine.refresh(gistId, filename);
                    runOnUiThread(() -> {
                        current = store.getNote(gistId);
                        if (!readDraftDirty && current != null) {
                            if (editPanel.getVisibility() != View.VISIBLE) render(current.content);
                        }
                        saving = false;
                        updateSyncStatus(false, current != null ? current.syncedAt : 0L, null);
                        NoteWidgetProvider.updateWidgetsForGist(this, gistId);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        saving = false;
                        updateSyncStatus(false, current != null ? current.syncedAt : 0L,
                                current != null && !current.content.isEmpty() ? "Offline · showing cached note" : error.getMessage());
                    });
                }
            });
        }
    }

    private void render(String content) {
        readDraft = content;
        readDraftDirty = false;
        if (content.isEmpty()) {
            readText.setText(getString(R.string.empty_note));
            readText.setTextColor(mutedTextColor());
            return;
        }
        readText.setTextColor(bodyTextColor());
        SpannableStringBuilder ssb = new SpannableStringBuilder(content);
        MarkdownStyler.format(ssb, bodyTextColor(), mutedTextColor(), readText.getTextSize(),
                getResources().getDisplayMetrics().density);
        readText.setText(ssb);
    }

    private boolean isTouchOnCheckbox(TextView view, MotionEvent event) {
        int x = (int) event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
        Layout layout = view.getLayout();
        if (layout == null) return false;

        int line = layout.getLineForVertical(y);
        if (line < 0 || line >= layout.getLineCount()) return false;
        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);

        CharSequence text = view.getText();
        if (!(text instanceof Spanned)) return false;
        Spanned spanned = (Spanned) text;

        TaskCheckboxSpan[] spans = spanned.getSpans(lineStart, lineEnd, TaskCheckboxSpan.class);
        for (TaskCheckboxSpan span : spans) {
            int spanStart = spanned.getSpanStart(span);
            int spanEnd = spanned.getSpanEnd(span);
            float spanLeft = layout.getPrimaryHorizontal(spanStart);
            float spanWidth = span.getSize(view.getPaint(), text, spanStart, spanEnd, null);

            float touchMin = spanLeft - dp(12);
            float touchMax = spanLeft + spanWidth + dp(16);
            if (x >= touchMin && x <= touchMax) {
                return true;
            }
        }
        return false;
    }

    private boolean handleCheckboxTouch(TextView view, MotionEvent event, boolean isEditing) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return isTouchOnCheckbox(view, event);
        }
        if (event.getAction() != MotionEvent.ACTION_UP) return false;
        int x = (int) event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
        Layout layout = view.getLayout();
        if (layout == null) return false;

        int line = layout.getLineForVertical(y);
        if (line < 0 || line >= layout.getLineCount()) return false;
        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);

        CharSequence text = view.getText();
        if (!(text instanceof Spanned)) return false;
        Spanned spanned = (Spanned) text;

        TaskCheckboxSpan[] spans = spanned.getSpans(lineStart, lineEnd, TaskCheckboxSpan.class);
        for (TaskCheckboxSpan span : spans) {
            int spanStart = spanned.getSpanStart(span);
            int spanEnd = spanned.getSpanEnd(span);
            float spanLeft = layout.getPrimaryHorizontal(spanStart);
            float spanWidth = span.getSize(view.getPaint(), text, spanStart, spanEnd, null);

            float touchMin = spanLeft - dp(12);
            float touchMax = spanLeft + spanWidth + dp(16);
            if (x >= touchMin && x <= touchMax) {
                int lineNumber = span.getLineNumber();
                boolean nextChecked = !span.isChecked();
                toggleTaskInView(lineNumber, nextChecked, isEditing);
                return true;
            }
        }
        return false;
    }

    private void toggleTaskInView(int lineNumber, boolean nextChecked, boolean isEditing) {
        if (isEditing) {
            String currentText = editor.getText().toString();
            int selStart = editor.getSelectionStart();
            int selEnd = editor.getSelectionEnd();
            String toggled = MarkdownTasks.toggle(currentText, lineNumber, nextChecked);
            if (!toggled.equals(currentText)) {
                editor.setText(toggled);
                editor.setSelection(Math.min(selStart, toggled.length()), Math.min(selEnd, toggled.length()));
                readDraft = toggled;
                save(toggled, false);
            }
        } else {
            String toggled = MarkdownTasks.toggle(readDraft, lineNumber, nextChecked);
            if (!toggled.equals(readDraft)) {
                readDraft = toggled;
                render(readDraft);
                save(readDraft, false);
            }
        }
    }

    private void beginEditing() {
        editor.setText(readDraft);
        editor.setSelection(editor.length());
        readPanel.setVisibility(View.GONE);
        editPanel.setVisibility(View.VISIBLE);
        setEditingToolsVisibility(View.VISIBLE);
        setOverlaySize(true);
        if (noteHeaderSubtitle != null) {
            String sub = (current != null && current.filename != null ? current.filename : filename)
                    + (localNote ? " · Local · Editing" : " · Editing");
            noteHeaderSubtitle.setText(sub);
        }
        if (headerBackButton != null) {
            headerBackButton.setVisibility(widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ? View.VISIBLE : View.GONE);
        }
        if (headerDoneButton != null) headerDoneButton.setVisibility(View.VISIBLE);
        if (headerCloseButton != null) {
            headerCloseButton.setVisibility(View.VISIBLE);
            headerCloseButton.setContentDescription("Cancel editing");
        }
        closeButton.setContentDescription(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID ? "Close" : "Cancel editing");
        editor.requestFocus();
        editor.postDelayed(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(editor, 0), 120);
    }

    private void ensureEditing() {
        if (editPanel.getVisibility() != View.VISIBLE) beginEditing();
    }

    private void showReading() {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(editor.getWindowToken(), 0);
        editPanel.setVisibility(View.GONE);
        readPanel.setVisibility(View.VISIBLE);
        setEditingToolsVisibility(View.GONE);
        setOverlaySize(false);
        if (noteHeaderSubtitle != null) {
            String sub = (current != null && current.filename != null ? current.filename : filename)
                    + (localNote ? " · Local · Preview" : " · Preview");
            noteHeaderSubtitle.setText(sub);
        }
        if (headerBackButton != null) {
            headerBackButton.setVisibility(View.VISIBLE);
        }
        if (headerDoneButton != null) headerDoneButton.setVisibility(View.GONE);
        if (headerCloseButton != null) {
            headerCloseButton.setVisibility(View.VISIBLE);
            headerCloseButton.setContentDescription("Close");
        }
        closeButton.setContentDescription("Close");
        render(current.content);
    }

    private void setEditingToolsVisibility(int visibility) {
        editActionRow.setVisibility(visibility);
        closeButton.setVisibility(visibility);
        readActionRow.setVisibility(visibility == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void setOverlaySize(boolean editing) {
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) noteSheet.getLayoutParams();
        params.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        params.setMargins(dp(16), dp(12), dp(16), dp(20));

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        if (editing) {
            params.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
        } else {
            // Preview mode: height is content height with min 50% screen and max 75% screen
            int minH = Math.round(screenHeight * 0.50f);
            int maxH = Math.round(screenHeight * 0.75f);

            int availableWidth = screenWidth - dp(32);
            int widthSpec = View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY);
            int unconstrainedSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

            noteHeader.measure(widthSpec, unconstrainedSpec);
            noteToolbar.measure(widthSpec, unconstrainedSpec);

            int textWidthSpec = View.MeasureSpec.makeMeasureSpec(Math.max(1, availableWidth - dp(40)), View.MeasureSpec.AT_MOST);
            readText.measure(textWidthSpec, unconstrainedSpec);

            int contentHeight = noteHeader.getMeasuredHeight() +
                    readText.getMeasuredHeight() +
                    noteToolbar.getMeasuredHeight() +
                    dp(2);

            int targetHeight = Math.max(minH, Math.min(contentHeight, maxH));
            params.height = targetHeight;
        }
        noteSheet.setLayoutParams(params);
        noteSheet.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        noteSheet.setClipToOutline(true);
        noteSheet.setElevation(dp(12));
    }

    private void insertTask() {
        MarkdownTasks.Edit edit = MarkdownTasks.insertTask(
                editor.getText().toString(), editor.getSelectionStart());
        editor.setText(edit.content);
        editor.setSelection(edit.cursor);
    }

    private void changeIndent(boolean indent) {
        MarkdownTasks.Edit edit = MarkdownTasks.changeIndent(
                editor.getText().toString(), editor.getSelectionStart(), indent);
        editor.setText(edit.content);
        editor.setSelection(edit.cursor);
    }

    private void save(String content, boolean fromEditor) {
        NoteStore.CachedNote base = current;
        store.putDraft(base, content);
        NoteStore.CachedNote draft = store.getNote(gistId);
        if (draft != null) current = draft;
        render(content);
        updateSyncStatus(current.dirty, current.syncedAt, null);
        if (fromEditor) showReading();
        NoteWidgetProvider.updateWidgetsForGist(this, gistId);
    }

    private void showConflictDiff(SyncEngine.ConflictException conflict) {
        updateSyncStatus(true, current.syncedAt, "Sync conflict · local edit kept on device");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog_conflict_diff)
                .setCancelable(true)
                .create();
        dialog.show();

        View root = dialog.findViewById(R.id.diff_title).getRootView();
        LinearLayout container = root.findViewById(R.id.diff_content_container);
        Button btnDiff = root.findViewById(R.id.btn_mode_diff);
        Button btnLocal = root.findViewById(R.id.btn_mode_local);
        Button btnRemote = root.findViewById(R.id.btn_mode_remote);
        Button btnKeepEditing = root.findViewById(R.id.btn_keep_editing);
        Button btnUseGithub = root.findViewById(R.id.btn_use_github);
        Button btnKeepDevice = root.findViewById(R.id.btn_keep_device);

        final int modeDiff = 0;
        final int modeLocal = 1;
        final int modeRemote = 2;

        Runnable[] renderMode = new Runnable[1];
        int[] currentMode = new int[]{modeDiff};

        renderMode[0] = () -> {
            container.removeAllViews();
            int inkColor = getColor(R.color.ink);
            int mutedColor = getColor(R.color.muted);

            btnDiff.setTextColor(currentMode[0] == modeDiff ? inkColor : mutedColor);
            btnDiff.setTypeface(null, currentMode[0] == modeDiff ? Typeface.BOLD : Typeface.NORMAL);
            btnLocal.setTextColor(currentMode[0] == modeLocal ? inkColor : mutedColor);
            btnLocal.setTypeface(null, currentMode[0] == modeLocal ? Typeface.BOLD : Typeface.NORMAL);
            btnRemote.setTextColor(currentMode[0] == modeRemote ? inkColor : mutedColor);
            btnRemote.setTypeface(null, currentMode[0] == modeRemote ? Typeface.BOLD : Typeface.NORMAL);

            if (currentMode[0] == modeDiff) {
                List<DiffHelper.DiffLine> lines = DiffHelper.compute(
                        conflict.remote != null ? conflict.remote.content : "", conflict.localContent);
                for (DiffHelper.DiffLine line : lines) {
                    TextView tv = new TextView(this);
                    tv.setTextSize(13f);
                    tv.setTypeface(Typeface.MONOSPACE);
                    tv.setPadding(dp(6), dp(3), dp(6), dp(3));
                    if (line.type == DiffHelper.DiffType.ADD) {
                        tv.setText("+ " + line.text);
                        tv.setTextColor(0xFF1B5E20);
                        tv.setBackgroundColor(0x224CAF50);
                    } else if (line.type == DiffHelper.DiffType.DELETE) {
                        tv.setText("- " + line.text);
                        tv.setTextColor(0xFFB71C1C);
                        tv.setBackgroundColor(0x22F44336);
                    } else {
                        tv.setText("  " + line.text);
                        tv.setTextColor(inkColor);
                        tv.setBackgroundColor(Color.TRANSPARENT);
                    }
                    container.addView(tv);
                }
            } else if (currentMode[0] == modeLocal) {
                TextView tv = new TextView(this);
                tv.setText(conflict.localContent);
                tv.setTextSize(13f);
                tv.setTextColor(inkColor);
                tv.setPadding(dp(6), dp(4), dp(6), dp(4));
                container.addView(tv);
            } else {
                TextView tv = new TextView(this);
                tv.setText(conflict.remote != null ? conflict.remote.content : "");
                tv.setTextSize(13f);
                tv.setTextColor(inkColor);
                tv.setPadding(dp(6), dp(4), dp(6), dp(4));
                container.addView(tv);
            }
        };

        btnDiff.setOnClickListener(v -> {
            currentMode[0] = modeDiff;
            renderMode[0].run();
        });
        btnLocal.setOnClickListener(v -> {
            currentMode[0] = modeLocal;
            renderMode[0].run();
        });
        btnRemote.setOnClickListener(v -> {
            currentMode[0] = modeRemote;
            renderMode[0].run();
        });

        btnKeepDevice.setOnClickListener(v -> {
            dialog.dismiss();
            forceSave(conflict.localContent);
        });

        btnUseGithub.setOnClickListener(v -> {
            dialog.dismiss();
            store.putRemote(conflict.remote);
            current = store.getNote(gistId);
            render(current.content);
            updateSyncStatus(false, current.syncedAt, null);
            NoteWidgetProvider.updateWidgetsForGist(this, gistId);
        });

        btnKeepEditing.setOnClickListener(v -> {
            dialog.dismiss();
            beginEditing();
        });

        renderMode[0].run();
    }

    private void forceSave(String content) {
        saving = true;
        if (syncTickIcon != null) syncTickIcon.setVisibility(View.GONE);
        syncStatus.setText("Replacing GitHub version…");
        executor.execute(() -> {
            try {
                GistNote saved = new GitHubApi(new TokenStore(this).getToken())
                        .updateGist(gistId, filename, content);
                store.putRemote(saved);
                runOnUiThread(() -> {
                    current = store.getNote(gistId);
                    render(current.content);
                    saving = false;
                    updateSyncStatus(false, current.syncedAt, null);
                    NoteWidgetProvider.updateWidgetsForGist(this, gistId);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    saving = false;
                    updateSyncStatus(true, current.syncedAt, "Replace failed · kept on device");
                });
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void initAppearance() {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appearance = store.getWidget(widgetId);
        }
        if (appearance == null && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            NoteStore.StackWidgetBinding stack = store.getStack(widgetId);
            if (stack != null) {
                for (NoteStore.StackNote note : stack.notes) {
                    if (note.gistId.equals(gistId)) {
                        appearance = stack.appearanceFor(note);
                        stackAppearance = true;
                        break;
                    }
                }
            }
        }
        if (appearance == null) {
            appearance = new NoteStore.WidgetBinding(
                    gistId, filename, "Transparent", "White", "16");
        }
    }

    private void applyAppearance() {
        if (appearance == null) return;
        int actualColor = getColor(R.color.ink);
        noteHeaderTitle.setTextColor(actualColor);
        if (noteHeaderSubtitle != null) {
            noteHeaderSubtitle.setTextColor(getColor(R.color.muted));
        }

        int blurRadius = getSharedPreferences("widget_style", MODE_PRIVATE).getInt("panel_blur", 48);
        if (blurRadius <= 0) blurRadius = 48;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                getWindow().setBackgroundBlurRadius(dp(blurRadius));
            } catch (Exception ignored) { }
        }
        GradientDrawable modalBg = new GradientDrawable();
        modalBg.setCornerRadius(dp(16));
        int surfaceBase = getColor(R.color.surface);
        // Flat opaque background with no shadows for maximum readability and minimalism
        modalBg.setColor((surfaceBase & 0xFFFFFF) | 0xFF000000);
        noteSheet.setBackground(modalBg);
        noteSheet.setElevation(0f);

        findViewById(R.id.note_workspace).setBackgroundColor(Color.TRANSPARENT);
        findViewById(R.id.note_header).setBackgroundColor(Color.TRANSPARENT);
        findViewById(R.id.note_toolbar).setBackgroundColor(Color.TRANSPARENT);
        noteHeader.setElevation(0f);
        noteToolbar.setElevation(0f);
        editor.setBackgroundColor(Color.TRANSPARENT);
        readPanel.setBackgroundColor(Color.TRANSPARENT);

        float textSize = bodyTextSize();
        readText.setTextSize(textSize);
        readText.setTextColor(actualColor);

        editor.setTextColor(actualColor);
        editor.setHintTextColor(mutedTextColor());
        editor.setTextSize(textSize);

        render(current.content);
        if (editor.getText().length() > 0) {
            MarkdownStyler.format(editor.getText(), actualColor, mutedTextColor(),
                    editor.getTextSize(), getResources().getDisplayMetrics().density);
        }
    }

    private int bodyTextColor() {
        return getColor(R.color.ink);
    }

    private int mutedTextColor() {
        return getColor(R.color.muted);
    }

    private float bodyTextSize() {
        return appearance == null ? 16f : NoteWidgetProvider.textSizeSp(appearance.textSize);
    }

    private static int backgroundColor(String name) {
        return NoteWidgetProvider.backgroundColorValue(name);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
