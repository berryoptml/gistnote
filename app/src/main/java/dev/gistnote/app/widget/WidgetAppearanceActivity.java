package dev.gistnote.app.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import dev.gistnote.app.R;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.ui.DetailedColorPickerDialog;

/** Dual-card local appearance and stack notes controls. Every change persists immediately. */
public final class WidgetAppearanceActivity extends Activity {
    private int widgetId, background, foreground, fontSize, blurRadius;
    private String alignment, stackGistId;
    private NoteStore store;
    private NoteStore.WidgetBinding initial;
    private NoteStore.StackWidgetBinding initialStack;
    private LinearLayout foregroundPalette, backgroundPalette, alignmentButtons;
    private GradientDrawable surface1, surface2;
    private SeekBar blurSlider, textTransparency, backgroundTransparency;
    private Consumer<Boolean> blurListener;
    private TextView notesCountBadge;
    private TextView addNotesLabel;
    private LinearLayout notesContainer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (Build.VERSION.SDK_INT >= 31) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }
        setFinishOnTouchOutside(true);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        store = new NoteStore(this);
        stackGistId = getIntent().getStringExtra(dev.gistnote.app.ui.NoteActivity.EXTRA_GIST_ID);
        initial = store.getWidget(widgetId);
        initialStack = store.getStack(widgetId);
        if (initial == null && initialStack != null) {
            for (NoteStore.StackNote item : initialStack.notes) {
                if (item.gistId.equals(stackGistId)) { initial = initialStack.appearanceFor(item); break; }
            }
            if (initial == null && !initialStack.notes.isEmpty()) {
                NoteStore.StackNote first = initialStack.notes.get(0);
                stackGistId = first.gistId;
                initial = initialStack.appearanceFor(first);
            }
        }
        if (initial == null) { finish(); return; }

        if (initialStack == null) {
            List<NoteStore.StackNote> singleList = new ArrayList<>();
            singleList.add(new NoteStore.StackNote(initial.gistId, initial.filename));
            initialStack = new NoteStore.StackWidgetBinding(singleList, initial.color, initial.textColor, initial.textSize);
            store.bindStack(widgetId, initialStack);
        }

        background = NoteWidgetProvider.backgroundColorValue(initial.color);
        foreground = NoteWidgetProvider.textColor(initial);
        fontSize = Math.round(NoteWidgetProvider.textSizeSp(initial.textSize));
        alignment = initial.alignment;
        blurRadius = getSharedPreferences("widget_style", MODE_PRIVATE).getInt("panel_blur", 52);

        // Root container
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Card 1: Style Settings
        LinearLayout card1 = new LinearLayout(this);
        card1.setOrientation(LinearLayout.VERTICAL);
        card1.setPadding(dp(6), dp(4), dp(6), dp(4));
        surface1 = new GradientDrawable();
        surface1.setCornerRadius(dp(18));
        card1.setBackground(surface1);
        card1.setElevation(dp(14));
        card1.setClipToOutline(true);

        LinearLayout colors = row();
        foregroundPalette = row();
        backgroundPalette = row();
        group(foregroundPalette);
        group(backgroundPalette);
        half(colors, foregroundPalette);
        half(colors, backgroundPalette);
        card1.addView(colors);
        rebuildPalettes();

        LinearLayout transparency = row();
        View textTransView = sliderWithOverlay("Text transparency", 0, "A",
                100, transparency(foreground), value -> {
                    foreground = withTransparency(foreground, value); applyLive();
                }, bar -> textTransparency = bar);
        View bgTransView = sliderWithOverlay("Background transparency", R.drawable.ic_paint, null,
                100, transparency(background), value -> {
                    background = withTransparency(background, value); applyLive();
                }, bar -> backgroundTransparency = bar);
        half(transparency, textTransView);
        half(transparency, bgTransView);
        card1.addView(transparency);

        LinearLayout bottom = row();
        LinearLayout sizeAndAlignment = row();
        sizeAndAlignment.setGravity(Gravity.CENTER_VERTICAL);
        group(sizeAndAlignment);

        TextView smaller = glyph("−", "Decrease text size", () -> {
            fontSize = Math.max(12, fontSize - 1); applyLive();
        });
        TextView larger = glyph("+", "Increase text size", () -> {
            fontSize = Math.min(28, fontSize + 1); applyLive();
        });
        smaller.setTextSize(20);
        larger.setTextSize(20);
        sizeAndAlignment.addView(smaller, new LinearLayout.LayoutParams(dp(32), dp(42)));
        sizeAndAlignment.addView(larger, new LinearLayout.LayoutParams(dp(32), dp(42)));

        View spacer = new View(this);
        sizeAndAlignment.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));

        alignmentButtons = row();
        sizeAndAlignment.addView(alignmentButtons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        renderAlignments();

        half(bottom, sizeAndAlignment);

        View blurContainer = sliderWithOverlay("Panel background blur", R.drawable.ic_blur, null,
                100, blurRadius, value -> {
                    blurRadius = value;
                    getSharedPreferences("widget_style", MODE_PRIVATE).edit().putInt("panel_blur", value).apply();
                    applyLive();
                    updateBlur();
                }, bar -> blurSlider = bar);
        half(bottom, blurContainer);
        card1.addView(bottom);

        View addDivider = new View(this);
        addDivider.setBackgroundColor(getColor(R.color.ink));
        addDivider.setAlpha(0.08f);
        LinearLayout.LayoutParams addDivParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        addDivParams.setMargins(dp(6), dp(4), dp(6), dp(2));
        card1.addView(addDivider, addDivParams);

        LinearLayout addNotesRow = new LinearLayout(this);
        addNotesRow.setOrientation(LinearLayout.HORIZONTAL);
        addNotesRow.setGravity(Gravity.CENTER_VERTICAL);
        addNotesRow.setPadding(dp(10), dp(8), dp(10), dp(8));
        addNotesRow.setClickable(true);
        addNotesRow.setFocusable(true);
        android.util.TypedValue outVal = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outVal, true);
        addNotesRow.setBackgroundResource(outVal.resourceId);

        ImageView addIcon = new ImageView(this);
        addIcon.setImageResource(R.drawable.ic_layers);
        addIcon.setColorFilter(getColor(R.color.ink));
        LinearLayout.LayoutParams addIconP = new LinearLayout.LayoutParams(dp(16), dp(16));
        addIconP.rightMargin = dp(8);
        addNotesRow.addView(addIcon, addIconP);

        addNotesLabel = new TextView(this);
        addNotesLabel.setText("Add notes to stack");
        addNotesLabel.setTextColor(getColor(R.color.ink));
        addNotesLabel.setTextSize(13.5f);
        addNotesLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        addNotesRow.addView(addNotesLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(this);
        chevron.setText("▾");
        chevron.setTextColor(getColor(R.color.ink));
        chevron.setTextSize(15f);
        addNotesRow.addView(chevron);

        card1.addView(addNotesRow);

        root.addView(card1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Card 2: Notes in Stack right below style card
        LinearLayout card2 = new LinearLayout(this);
        card2.setOrientation(LinearLayout.VERTICAL);
        card2.setPadding(dp(12), dp(10), dp(12), dp(10));
        surface2 = new GradientDrawable();
        surface2.setCornerRadius(dp(18));
        card2.setBackground(surface2);
        card2.setElevation(dp(12));
        card2.setClipToOutline(true);

        LinearLayout.LayoutParams card2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card2Params.topMargin = dp(10);
        card2.setLayoutParams(card2Params);

        // Header Row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(0), dp(2), dp(6));

        ImageView layersIcon = new ImageView(this);
        layersIcon.setImageResource(R.drawable.ic_layers);
        layersIcon.setColorFilter(getColor(R.color.ink));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(17), dp(17));
        iconParams.rightMargin = dp(8);
        header.addView(layersIcon, iconParams);

        TextView titleView = new TextView(this);
        titleView.setText("Notes in Stack");
        titleView.setTextColor(getColor(R.color.ink));
        titleView.setTextSize(14f);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(titleView);

        View headerSpacer = new View(this);
        header.addView(headerSpacer, new LinearLayout.LayoutParams(0, 0, 1f));

        notesCountBadge = new TextView(this);
        notesCountBadge.setTextColor(getColor(R.color.ink));
        notesCountBadge.setAlpha(0.65f);
        notesCountBadge.setTextSize(12f);
        header.addView(notesCountBadge);

        card2.addView(header);

        // Subtle divider
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.ink));
        divider.setAlpha(0.10f);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divParams.bottomMargin = dp(4);
        card2.addView(divider, divParams);

        // Scrollable list of notes
        ScrollView scroll = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int max = dp(230);
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                if (mode == MeasureSpec.UNSPECIFIED || size > max) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        notesContainer = new LinearLayout(this);
        notesContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(notesContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card2.addView(scroll);
        card2.setVisibility(View.GONE);

        addNotesRow.setOnClickListener(v -> {
            if (card2.getVisibility() == View.VISIBLE) {
                card2.setVisibility(View.GONE);
                chevron.setText("▾");
            } else {
                card2.setVisibility(View.VISIBLE);
                chevron.setText("▴");
                populateNotesList();
            }
        });

        root.addView(card2);
        setContentView(root);

        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        attributes.y = dp(16);
        // Frost reads best against the real launcher, without a dark scrim over it.
        attributes.dimAmount = 0f;
        if (Build.VERSION.SDK_INT >= 31) {
            attributes.setBlurBehindRadius(dp(blurRadius > 0 ? blurRadius : 52));
        }
        getWindow().setAttributes(attributes);
        getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(24),
                ViewGroup.LayoutParams.WRAP_CONTENT);

        populateNotesList();
        dev.gistnote.app.ui.TooltipHelper.applyTooltips(root);
        if (Build.VERSION.SDK_INT >= 31) {
            blurListener = enabled -> updateBlur();
            getWindowManager().addCrossWindowBlurEnabledListener(blurListener);
        }
        updateBlur();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::finishAndRemoveTask);
        }
    }

    private void updateBlur() {
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        boolean liveBlur = false;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                liveBlur = getWindowManager().isCrossWindowBlurEnabled();
                int radius = dp(blurRadius > 0 ? blurRadius : 52);
                getWindow().setBackgroundBlurRadius(radius);
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.setBlurBehindRadius(radius);
                getWindow().setAttributes(lp);
            } catch (Exception ignored) { }
        }

        // Keep the launcher legible through the sheet when live blur is available.
        // If the system disables blur (battery saver, developer setting, OEM policy),
        // use a denser tint so controls remain readable rather than transparent.
        int base = dark ? Color.rgb(31, 34, 41) : Color.rgb(240, 245, 250);
        int alpha = liveBlur ? (dark ? 0xA6 : 0xA2) : (dark ? 0xF2 : 0xF4);
        int highlight = blend(base, Color.WHITE, dark ? 0.18f : 0.44f);
        int lowlight = blend(base, dark ? Color.BLACK : Color.rgb(205, 214, 226), 0.18f);
        int top = Color.argb(alpha, Color.red(highlight), Color.green(highlight), Color.blue(highlight));
        int bottom = Color.argb(alpha, Color.red(lowlight), Color.green(lowlight), Color.blue(lowlight));
        int edge = dark ? 0x55FFFFFF : 0x8AFFFFFF;
        applyGlassSurface(surface1, top, bottom, edge);
        applyGlassSurface(surface2, top, bottom, edge);
    }

    private void applyGlassSurface(GradientDrawable surface, int top, int bottom, int edge) {
        if (surface == null) return;
        surface.setOrientation(GradientDrawable.Orientation.TL_BR);
        int middleRgb = blend(top, bottom, 0.45f);
        int middle = Color.argb(Color.alpha(top), Color.red(middleRgb),
                Color.green(middleRgb), Color.blue(middleRgb));
        surface.setColors(new int[]{top, middle, bottom});
        surface.setStroke(dp(1), edge);
    }

    private static int blend(int from, int to, float amount) {
        float safe = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) * (1f - safe) + Color.red(to) * safe),
                Math.round(Color.green(from) * (1f - safe) + Color.green(to) * safe),
                Math.round(Color.blue(from) * (1f - safe) + Color.blue(to) * safe));
    }

    private LinearLayout row() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    private void half(LinearLayout parent, View child) {
        parent.addView(child, groupParams(0, 1));
    }

    private LinearLayout.LayoutParams groupParams(int width, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(42), weight);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        return params;
    }

    private void group(View view) {
        GradientDrawable background = new GradientDrawable();
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        background.setColor(dark ? 0x2FFFFFFF : 0x48FFFFFF);
        background.setStroke(dp(1), dark ? 0x48FFFFFF : 0x72FFFFFF);
        background.setCornerRadius(dp(10));
        view.setBackground(background);
    }

    private TextView glyph(String text, String description, Runnable action) {
        TextView result = new TextView(this);
        result.setText(text);
        result.setTextSize(17);
        result.setTextColor(getColor(R.color.ink));
        result.setGravity(Gravity.CENTER);
        result.setContentDescription(description);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        result.setBackgroundResource(outValue.resourceId);
        result.setOnClickListener(v -> action.run());
        return result;
    }

    private void rebuildPalettes() {
        fillPalette(foregroundPalette, false);
        fillPalette(backgroundPalette, true);
    }

    private void fillPalette(LinearLayout palette, boolean bg) {
        palette.removeAllViews();
        int[] favorites = WidgetStyleFavorites.load(this, bg);
        for (int index = 0; index < favorites.length; index++) {
            final int slot = index;
            int color = favorites[index];
            FrameLayout target = new FrameLayout(this);
            View swatch = new View(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            boolean selected = ((bg ? background : foreground) & 0xFFFFFF) == (color & 0xFFFFFF);
            swatch.setBackground(circle);
            target.addView(swatch, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            target.setContentDescription(String.format(Locale.ROOT, "%s color #%06X%s",
                    bg ? "Background" : "Text", color & 0xFFFFFF, selected ? ", selected" : ""));
            target.setOnClickListener(v -> chooseColor(bg, color));
            target.setOnLongClickListener(v -> {
                DetailedColorPickerDialog.showBelow(this, bg ? "Replace background favorite" : "Replace text favorite",
                        ((bg ? background : foreground) & 0xFF000000) | (color & 0xFFFFFF), chosen -> {
                            int[] updated = WidgetStyleFavorites.load(this, bg);
                            updated[slot] = chosen | 0xFF000000;
                            WidgetStyleFavorites.save(this, bg, updated);
                            choosePickerColor(bg, chosen);
                        });
                return true;
            });
            palette.addView(target, new LinearLayout.LayoutParams(0, dp(40), 1));
        }
        View picker = new View(this) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                float x = getWidth() / 2f, y = getHeight() / 2f;
                paint.setShader(new android.graphics.SweepGradient(x, y,
                        new int[] {0xFFFF6B6B, 0xFFFFD166, 0xFF8BE0C5, 0xFF65BAEE, 0xFFC18ED1, 0xFFFF6B6B}, null));
                canvas.drawCircle(x, y, dp(12), paint);
            }
        };
        picker.setContentDescription(bg ? "Pick background color" : "Pick text color");
        picker.setOnClickListener(v -> DetailedColorPickerDialog.showBelow(this,
                bg ? "Background color" : "Text color", bg ? background : foreground,
                color -> choosePickerColor(bg, color)));
        palette.addView(picker, new LinearLayout.LayoutParams(0, dp(40), 1));
    }

    private void chooseColor(boolean bg, int color) {
        if (bg) background = (background & 0xFF000000) | (color & 0xFFFFFF);
        else foreground = (foreground & 0xFF000000) | (color & 0xFFFFFF);
        rebuildPalettes(); applyLive();
    }

    private void choosePickerColor(boolean bg, int color) {
        if (bg) background = color; else foreground = color;
        textTransparency.setProgress(transparency(foreground));
        backgroundTransparency.setProgress(transparency(background));
        rebuildPalettes(); applyLive();
    }


    private void renderAlignments() {
        alignmentButtons.removeAllViews();
        String[] values = {"start", "center", "end", "justify"};
        String[] names = {"Align left", "Align center", "Align right", "Justify"};
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            final int mode = i;
            View icon = new View(this) {
                private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(android.graphics.Canvas canvas) {
                    super.onDraw(canvas);
                    paint.setColor(getColor(value.equals(alignment) ? R.color.focus_accent : R.color.ink));
                    paint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
                    float width = dp(14), left = (getWidth() - width) / 2f;
                    float top = (getHeight() - dp(12)) / 2f;
                    for (int line = 0; line < 4; line++) {
                        float length = mode == 3 || line % 2 == 0 ? width : dp(9);
                        float x = left + (mode == 1 ? (width - length) / 2 : mode == 2 ? width - length : 0);
                        canvas.drawLine(x, top + dp(line * 4), x + length, top + dp(line * 4), paint);
                    }
                }
            };
            icon.setContentDescription(names[i]);
            icon.setSelected(value.equals(alignment));
            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
            icon.setBackgroundResource(outValue.resourceId);
            icon.setOnClickListener(v -> { alignment = value; renderAlignments(); applyLive(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(26), dp(42));
            params.setMargins(dp(1), 0, dp(1), 0);
            alignmentButtons.addView(icon, params);
        }
    }

    private interface Progress { void changed(int value); }
    private interface SeekBarRef { void accept(SeekBar bar); }

    private View sliderWithOverlay(String description, int iconRes, String textGlyph,
                                   int max, int progress, Progress change,
                                   SeekBarRef ref) {
        FrameLayout container = new FrameLayout(this);
        group(container);

        SeekBar bar = new SeekBar(this);
        DetailedColorPickerDialog.styleSlider(this, bar);
        bar.setMax(max);
        bar.setProgress(progress);
        bar.setContentDescription(description);
        bar.setPadding(dp(38), dp(4), dp(12), dp(4));
        bar.setThumbOffset(dp(2));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) change.changed(value);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        barParams.gravity = Gravity.CENTER_VERTICAL;
        container.addView(bar, barParams);

        if (iconRes != 0) {
            ImageView iconView = new ImageView(this);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(getColor(R.color.ink));
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(22), dp(22));
            iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            iconParams.leftMargin = dp(8);
            container.addView(iconView, iconParams);
        } else if (textGlyph != null) {
            TextView label = new TextView(this);
            label.setText(textGlyph);
            label.setTextColor(getColor(R.color.ink));
            label.setTextSize(14);
            label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(dp(22), dp(22));
            labelParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            labelParams.leftMargin = dp(8);
            container.addView(label, labelParams);
        }

        if (ref != null) ref.accept(bar);
        return container;
    }

    private void applyLive() {
        NoteStore.WidgetBinding binding = new NoteStore.WidgetBinding(initial.gistId, initial.filename,
                String.format(Locale.ROOT, "#%08X", background),
                String.format(Locale.ROOT, "#%08X", foreground), Integer.toString(fontSize), alignment);
        if (initialStack != null) {
            store.bindStack(widgetId, initialStack.withAppearance(stackGistId, binding));
            StackNoteWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
        } else {
            store.bindWidget(widgetId, binding);
            NoteWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
        }
    }

    private void populateNotesList() {
        if (notesContainer == null) return;
        List<dev.gistnote.app.data.GistNote> cached = store.getAllCachedNotes();
        if (cached == null || cached.isEmpty()) {
            notesContainer.removeAllViews();
            TextView loading = new TextView(this);
            loading.setText("Loading notes…");
            loading.setTextColor(getColor(R.color.ink));
            loading.setAlpha(0.5f);
            loading.setPadding(dp(4), dp(12), dp(4), dp(12));
            notesContainer.addView(loading);

            String token = new dev.gistnote.app.security.TokenStore(this).getToken();
            if (!token.isEmpty()) {
                new Thread(() -> {
                    try {
                        List<dev.gistnote.app.data.GistNote> loaded = new dev.gistnote.app.data.GitHubApi(token).listGists();
                        store.saveGists(loaded);
                        runOnUiThread(() -> renderNotesList(store.getAllCachedNotes()));
                    } catch (Exception ignored) {
                        runOnUiThread(() -> {
                            if (notesContainer != null) {
                                notesContainer.removeAllViews();
                                TextView err = new TextView(this);
                                err.setText("Could not load notes");
                                err.setTextColor(getColor(R.color.ink));
                                err.setAlpha(0.5f);
                                err.setPadding(dp(4), dp(12), dp(4), dp(12));
                                notesContainer.addView(err);
                            }
                        });
                    }
                }).start();
            }
            return;
        }
        renderNotesList(cached);
    }

    private void renderNotesList(List<dev.gistnote.app.data.GistNote> notes) {
        if (notesContainer == null) return;
        notesContainer.removeAllViews();
        if (notes == null || notes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No notes available");
            empty.setTextColor(getColor(R.color.ink));
            empty.setAlpha(0.5f);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            notesContainer.addView(empty);
            if (notesCountBadge != null) notesCountBadge.setText("0 notes");
            return;
        }

        NoteStore.StackWidgetBinding currentStack = store.getStack(widgetId);
        LinkedHashSet<String> selectedGistIds = new LinkedHashSet<>();
        if (currentStack != null) {
            for (NoteStore.StackNote sn : currentStack.notes) {
                selectedGistIds.add(sn.gistId);
            }
        } else if (initial != null) {
            selectedGistIds.add(initial.gistId);
        }

        List<dev.gistnote.app.data.GistNote> ordered = new ArrayList<>();
        for (dev.gistnote.app.data.GistNote g : notes) {
            if (selectedGistIds.contains(g.id)) ordered.add(g);
        }
        for (dev.gistnote.app.data.GistNote g : notes) {
            if (!selectedGistIds.contains(g.id)) ordered.add(g);
        }

        updateBadgeText(selectedGistIds.size());

        for (dev.gistnote.app.data.GistNote g : ordered) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(7), dp(4), dp(7));

            android.util.TypedValue outVal = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outVal, true);
            row.setBackgroundResource(outVal.resourceId);

            CheckBox cb = new CheckBox(this);
            cb.setChecked(selectedGistIds.contains(g.id));
            cb.setClickable(false);
            cb.setFocusable(false);
            if (Build.VERSION.SDK_INT >= 21) {
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.ink)));
            }
            LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cbParams.rightMargin = dp(6);
            row.addView(cb, cbParams);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView titleTv = new TextView(this);
            String titleText = (g.title != null && !g.title.isEmpty()) ? g.title : g.filename;
            titleTv.setText(titleText);
            titleTv.setTextColor(getColor(R.color.ink));
            titleTv.setTextSize(13.5f);
            titleTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(titleTv);

            TextView subTv = new TextView(this);
            subTv.setText(g.filename);
            subTv.setTextColor(getColor(R.color.ink));
            subTv.setAlpha(0.55f);
            subTv.setTextSize(11.5f);
            subTv.setSingleLine(true);
            subTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(subTv);

            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnClickListener(v -> {
                boolean wasChecked = selectedGistIds.contains(g.id);
                if (wasChecked) {
                    if (selectedGistIds.size() <= 1) {
                        android.widget.Toast.makeText(this, "At least 1 note required in stack", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedGistIds.remove(g.id);
                    cb.setChecked(false);
                } else {
                    selectedGistIds.add(g.id);
                    cb.setChecked(true);
                }

                List<NoteStore.StackNote> newNotes = new ArrayList<>();
                for (dev.gistnote.app.data.GistNote gn : notes) {
                    if (selectedGistIds.contains(gn.id)) {
                        newNotes.add(new NoteStore.StackNote(gn.id, gn.filename));
                    }
                }

                NoteStore.StackWidgetBinding updated;
                NoteStore.StackWidgetBinding stack = store.getStack(widgetId);
                if (stack != null) {
                    updated = stack.withNotes(newNotes);
                } else {
                    updated = new NoteStore.StackWidgetBinding(newNotes,
                            initial != null ? initial.color : "Transparent",
                            initial != null ? initial.textColor : "White",
                            initial != null ? initial.textSize : "Medium");
                }
                store.bindStack(widgetId, updated);
                initialStack = updated;
                StackNoteWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
                updateBadgeText(selectedGistIds.size());
            });

            notesContainer.addView(row);
        }
    }

    private void updateBadgeText(int count) {
        if (notesCountBadge != null) {
            notesCountBadge.setText(count + (count == 1 ? " note" : " notes"));
        }
        if (addNotesLabel != null) {
            addNotesLabel.setText("Add notes to stack (" + count + (count == 1 ? " note" : " notes") + ")");
        }
    }

    private static int transparency(int color) { return Math.round((255 - Color.alpha(color)) * 100f / 255f); }
    private static int withTransparency(int color, int percent) {
        return ((255 - Math.round(percent * 255f / 100f)) << 24) | (color & 0xFFFFFF);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onBackPressed() { finishAndRemoveTask(); }
    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 31 && blurListener != null)
            getWindowManager().removeCrossWindowBlurEnabledListener(blurListener);
        super.onDestroy();
    }
}
