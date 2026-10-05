package dev.gistnote.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.security.TokenStore;
import dev.gistnote.app.widget.NoteWidgetProvider;
import dev.gistnote.app.widget.WidgetPinning;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TokenStore tokenStore;
    private NoteStore noteStore;
    private TextView status;
    private TextView headerTitle;
    private View createButton;
    private View refreshButton;
    private GistAdapter adapter;
    private GridView grid;
    private boolean gridMode;

    // Header & Search Views
    private View normalHeader;
    private View searchHeader;
    private EditText searchInput;
    private View searchClearButton;

    // Selection Views
    private View selectionHeader;
    private View selectionCloseButton;
    private TextView selectionCountText;
    private TextView selectionSelectAll;
    private View selectionCreateStackButton;
    private View fabContainer;

    // Tab Views
    private TextView tabPinned;
    private TextView tabLocal;
    private TextView tabAll;
    private TextView tabArchived;
    private static final int TAB_PINNED = 0;
    private static final int TAB_LOCAL = 1;
    private static final int TAB_ALL = 2;
    private static final int TAB_ARCHIVED = 3;
    private int selectedTab = TAB_PINNED;
    private String searchQuery = "";
    private List<GistNote> allNotes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        SystemBars.apply(findViewById(R.id.main_root));
        tokenStore = new TokenStore(this);
        noteStore = new NoteStore(this);

        headerTitle = findViewById(R.id.header_title);
        updateGreeting();
        status = findViewById(R.id.status_text);
        createButton = findViewById(R.id.create_button);
        refreshButton = findViewById(R.id.refresh_button);
        fabContainer = findViewById(R.id.fab_container);
        grid = findViewById(R.id.gist_list);
        gridMode = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("grid_mode", false);
        adapter = new GistAdapter(this, gridMode);
        adapter.setOnPinClickListener(note -> {
            noteStore.togglePinned(note.id);
            applyFilter();
        });
        adapter.setOnArchiveClickListener(note -> {
            noteStore.toggleArchived(note.id);
            applyFilter();
        });
        grid.setAdapter(adapter);

        initHeaderAndSearch();
        initSelectionHeader();
        initTabs();

        // Load cached gists immediately so the screen is populated instantly
        updateFromCache();

        applyLayoutMode();
        grid.setOnItemClickListener((parent, view, position, id) -> {
            GistNote note = adapter.getItem(position);
            if (adapter.isSelectionMode()) {
                adapter.toggleSelection(note.id);
                updateSelectionUI();
            } else {
                open(note);
            }
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            GistNote note = adapter.getItem(position);
            if (adapter.isSelectionMode()) {
                adapter.toggleSelection(note.id);
                updateSelectionUI();
                return true;
            }
            WidgetPinning.showChoices(this, note, () -> enterSelectionMode(note));
            return true;
        });

        findViewById(R.id.settings_button).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        createButton.setOnClickListener(v ->
                startActivity(new Intent(this, CreateGistActivity.class)));
        refreshButton.setOnClickListener(v -> refresh());
        status.setOnClickListener(v -> {
            if (tokenStore.hasToken()) refresh();
        });
        findViewById(R.id.view_toggle_button).setOnClickListener(v -> {
            gridMode = !gridMode;
            getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("grid_mode", gridMode).apply();
            adapter.setGrid(gridMode);
            applyLayoutMode();
        });
        TooltipHelper.applyTooltips(findViewById(android.R.id.content));
    }

    private void initHeaderAndSearch() {
        normalHeader = findViewById(R.id.normal_header);
        searchHeader = findViewById(R.id.search_header);
        searchInput = findViewById(R.id.search_input);
        searchClearButton = findViewById(R.id.search_clear_button);
        View searchButton = findViewById(R.id.search_button);
        View searchCloseButton = findViewById(R.id.search_close_button);

        searchButton.setOnClickListener(v -> expandSearch());
        searchCloseButton.setOnClickListener(v -> collapseSearch());
        searchClearButton.setOnClickListener(v -> searchInput.setText(""));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                searchClearButton.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });
    }

    private void initSelectionHeader() {
        selectionHeader = findViewById(R.id.selection_header);
        selectionCloseButton = findViewById(R.id.selection_close_button);
        selectionCountText = findViewById(R.id.selection_count_text);
        selectionSelectAll = findViewById(R.id.selection_select_all);
        selectionCreateStackButton = findViewById(R.id.selection_create_stack_button);

        selectionCloseButton.setOnClickListener(v -> exitSelectionMode());
        selectionSelectAll.setOnClickListener(v -> {
            int selectedCount = adapter.getSelectedIds().size();
            if (selectedCount == adapter.getCount() && selectedCount > 0) {
                adapter.clearSelection();
            } else {
                adapter.selectAll();
            }
            updateSelectionUI();
        });
        selectionCreateStackButton.setOnClickListener(v -> {
            List<GistNote> selected = adapter.getSelectedNotes();
            if (selected.isEmpty()) {
                android.widget.Toast.makeText(this, "Select at least 1 note to create a stack", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            WidgetPinning.createStackFromSelected(this, selected, this::exitSelectionMode);
        });
    }

    private void enterSelectionMode(GistNote note) {
        if (searchHeader != null && searchHeader.getVisibility() == View.VISIBLE) {
            collapseSearch();
        }
        adapter.setSelectionMode(true);
        adapter.selectNote(note.id, true);
        normalHeader.setVisibility(View.GONE);
        if (searchHeader != null) searchHeader.setVisibility(View.GONE);
        selectionHeader.setVisibility(View.VISIBLE);
        if (fabContainer != null) fabContainer.setVisibility(View.GONE);
        updateSelectionUI();
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        selectionHeader.setVisibility(View.GONE);
        normalHeader.setVisibility(View.VISIBLE);
        if (fabContainer != null) fabContainer.setVisibility(View.VISIBLE);
    }

    private void updateSelectionUI() {
        int count = adapter.getSelectedIds().size();
        selectionCountText.setText(count + " selected");
        selectionSelectAll.setText(count == adapter.getCount() && count > 0 ? "Deselect all" : "Select all");
        selectionCreateStackButton.setEnabled(count > 0);
        selectionCreateStackButton.setAlpha(count > 0 ? 1.0f : 0.4f);
    }

    private void expandSearch() {
        if (adapter.isSelectionMode()) {
            exitSelectionMode();
        }
        normalHeader.setVisibility(View.GONE);
        searchHeader.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void collapseSearch() {
        searchInput.setText("");
        searchQuery = "";
        searchHeader.setVisibility(View.GONE);
        normalHeader.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        applyFilter();
    }

    private void initTabs() {
        tabPinned = findViewById(R.id.tab_pinned);
        tabLocal = findViewById(R.id.tab_local);
        tabAll = findViewById(R.id.tab_all);
        tabArchived = findViewById(R.id.tab_archived);

        tabPinned.setOnClickListener(v -> selectTab(TAB_PINNED));
        tabLocal.setOnClickListener(v -> selectTab(TAB_LOCAL));
        tabAll.setOnClickListener(v -> selectTab(TAB_ALL));
        tabArchived.setOnClickListener(v -> selectTab(TAB_ARCHIVED));
        updateTabUI();
    }

    private void selectTab(int tab) {
        if (selectedTab == tab) return;
        if (adapter.isSelectionMode()) {
            exitSelectionMode();
        }
        selectedTab = tab;
        updateTabUI();
        applyFilter();
    }

    private void updateTabUI() {
        updateTab(tabPinned, selectedTab == TAB_PINNED);
        updateTab(tabLocal, selectedTab == TAB_LOCAL);
        updateTab(tabAll, selectedTab == TAB_ALL);
        updateTab(tabArchived, selectedTab == TAB_ARCHIVED);
    }

    private void updateTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.pill_tab_selected : R.drawable.pill_tab_unselected);
        tab.setTextColor(getColor(selected ? R.color.home_bg : R.color.home_muted));
        tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void applyFilter() {
        List<GistNote> filtered = new ArrayList<>();
        for (GistNote note : allNotes) {
            boolean archived = noteStore.isArchived(note.id);
            boolean local = NoteStore.isLocalId(note.id);
            if (selectedTab == TAB_PINNED && (archived || !noteStore.isPinned(note.id))) continue;
            if (selectedTab == TAB_LOCAL && (archived || !local)) continue;
            if (selectedTab == TAB_ALL && archived) continue;
            if (selectedTab == TAB_ARCHIVED && (!archived || !local)) continue;
            if (!searchQuery.isEmpty()) {
                String title = note.title != null ? note.title.toLowerCase(Locale.ROOT) : "";
                String content = note.content != null ? note.content.toLowerCase(Locale.ROOT) : "";
                if (!title.contains(searchQuery) && !content.contains(searchQuery)) {
                    continue;
                }
            }
            filtered.add(note);
        }
        adapter.setNotes(filtered);

        if (filtered.isEmpty()) {
            status.setVisibility(View.VISIBLE);
            if (!searchQuery.isEmpty()) {
                status.setText("No notes found matching \"" + searchQuery + "\"");
            } else if (selectedTab == TAB_PINNED) {
                status.setText("No pinned notes yet. Tap the pin icon on any note to pin it here.");
            } else if (selectedTab == TAB_LOCAL) {
                status.setText("No local notes yet. Tap + to create one on this device.");
            } else if (selectedTab == TAB_ARCHIVED) {
                status.setText("No archived local notes.");
            } else if (tokenStore.hasToken()) {
                status.setText("No cached gists. Tap ↻ to sync.");
            } else {
                status.setText("Create a local note, or add a GitHub token in Settings.");
            }
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private void applyLayoutMode() {
        grid.setNumColumns(gridMode ? 2 : 1);
        grid.setStretchMode(gridMode ? GridView.NO_STRETCH : GridView.STRETCH_COLUMN_WIDTH);
        grid.post(this::applyMeasuredColumnWidth);
        View toggle = findViewById(R.id.view_toggle_button);
        toggle.setContentDescription(gridMode ? "Switch to list view" : "Switch to grid view");
        ((ImageButton) toggle).setImageResource(gridMode
                ? R.drawable.ic_list_view : R.drawable.ic_grid_view);
    }

    private void applyMeasuredColumnWidth() {
        int available = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int spacing = grid.getHorizontalSpacing();
        int columns = gridMode ? 2 : 1;
        int columnWidth = (available - spacing * (columns - 1)) / columns;
        if (columnWidth > 0) grid.setColumnWidth(columnWidth);
    }

    @Override protected void onResume() {
        super.onResume();
        updateGreeting();
        NoteWidgetProvider.updateAllWidgets(this);
        dev.gistnote.app.widget.StackNoteWidgetProvider.notifyAllChanged(this);
        updateFromCache();
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        if (searchHeader != null && searchHeader.getVisibility() == View.VISIBLE) {
            collapseSearch();
            return;
        }
        super.onBackPressed();
    }

    private void updateGreeting() {
        if (headerTitle == null) return;
        String username = tokenStore.getUsername();
        if (!username.isEmpty()) {
            headerTitle.setText("Hi, " + username);
        } else {
            headerTitle.setText("Hi there");
        }
    }

    private void updateFromCache() {
        List<GistNote> cached = noteStore.getAllCachedNotes();
        if (cached != null && !cached.isEmpty()) {
            allNotes = withCachedPreviews(cached);
        } else if (adapter.getCount() > 0) {
            allNotes = withCachedPreviews(adapter.getNotes());
        } else {
            allNotes = new ArrayList<>();
        }
        applyFilter();
        createButton.setEnabled(true);
        refreshButton.setEnabled(tokenStore.hasToken());
    }

    private void refresh() {
        if (!tokenStore.hasToken()) {
            updateFromCache();
            if (allNotes.isEmpty()) {
                status.setVisibility(View.VISIBLE);
                status.setText("Create a local note, or add a GitHub token in Settings.");
            }
            refreshButton.setEnabled(false);
            return;
        }
        createButton.setEnabled(true);
        refreshButton.setEnabled(false);
        status.setVisibility(View.VISIBLE);
        status.setText("Syncing your gists…");
        executor.execute(() -> {
            try {
                if (tokenStore.getUsername().isEmpty()) {
                    try {
                        String login = new GitHubApi(tokenStore.getToken()).getLogin();
                        tokenStore.setUsername(login);
                        runOnUiThread(this::updateGreeting);
                    } catch (Exception ignored) { }
                }
                List<GistNote> notes = new GitHubApi(tokenStore.getToken()).listGists();
                List<GistNote> prepared = withCachedPreviews(notes);
                noteStore.saveGists(prepared);
                runOnUiThread(() -> {
                    allNotes = withCachedPreviews(noteStore.getAllCachedNotes());
                    applyFilter();
                    refreshButton.setEnabled(true);
                });
                GitHubApi api = new GitHubApi(tokenStore.getToken());
                for (GistNote note : prepared) {
                    if (Thread.currentThread().isInterrupted() || isFinishing() || isDestroyed()) break;
                    NoteStore.CachedNote cached = noteStore.getNote(note.id);
                    if (cached != null && cached.content != null && !cached.content.isEmpty()) {
                        runOnUiThread(() -> {
                            adapter.updatePreview(note.id, cached.content);
                            for (int i = 0; i < allNotes.size(); i++) {
                                if (allNotes.get(i).id.equals(note.id)) {
                                    allNotes.set(i, allNotes.get(i).withContent(cached.content));
                                    break;
                                }
                            }
                        });
                    }
                    if (cached == null || !cached.dirty) {
                        try {
                            GistNote full = api.getGist(note.id, note.filename);
                            noteStore.putRemote(full);
                            runOnUiThread(() -> {
                                adapter.updatePreview(note.id, full.content);
                                for (int i = 0; i < allNotes.size(); i++) {
                                    if (allNotes.get(i).id.equals(note.id)) {
                                        allNotes.set(i, allNotes.get(i).withContent(full.content));
                                        break;
                                    }
                                }
                            });
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (allNotes.isEmpty()) {
                        updateFromCache();
                    }
                    if (allNotes.isEmpty()) {
                        status.setVisibility(View.VISIBLE);
                        status.setText(error.getMessage());
                    }
                    refreshButton.setEnabled(true);
                });
            }
        });
    }

    private List<GistNote> withCachedPreviews(List<GistNote> notes) {
        if (notes == null) return new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            GistNote note = notes.get(i);
            NoteStore.CachedNote cached = noteStore.getNote(note.id);
            if (cached != null && cached.content != null && !cached.content.isEmpty()) {
                notes.set(i, note.withContent(cached.content));
            }
        }
        return notes;
    }

    private void open(GistNote note) {
        Intent intent = new Intent(this, NoteActivity.class)
                .putExtra(NoteActivity.EXTRA_GIST_ID, note.id)
                .putExtra(NoteActivity.EXTRA_FILENAME, note.filename)
                .putExtra(NoteActivity.EXTRA_TITLE, note.title);
        startActivity(intent);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
