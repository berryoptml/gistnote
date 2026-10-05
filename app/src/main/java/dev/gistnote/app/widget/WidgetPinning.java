package dev.gistnote.app.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.NoteStore;

public final class WidgetPinning {
    private WidgetPinning() { }

    public static void request(Activity activity, GistNote note) {
        if (note.isPublic) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.public_widget_title)
                    .setMessage(R.string.public_widget_warning)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.add_public_widget,
                            (dialog, which) -> requestConfirmed(activity, note))
                    .show();
            return;
        }
        requestConfirmed(activity, note);
    }

    /** Entry point for long-pressing a gist on the home screen. */
    public static void showChoices(Activity activity, GistNote note) {
        showChoices(activity, note, null);
    }

    public static void showChoices(Activity activity, GistNote note, Runnable onSelectMultiple) {
        if (note == null) return;
        if (note.isPublic) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.public_widget_title)
                    .setMessage(R.string.public_widget_warning)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.add_public_widget,
                            (dialog, which) -> showWidgetGridModal(activity, Collections.singletonList(note), onSelectMultiple, null))
                    .show();
            return;
        }
        showWidgetGridModal(activity, Collections.singletonList(note), onSelectMultiple, null);
    }

    public static void createStackFromSelected(Activity activity, List<GistNote> notes, Runnable onComplete) {
        if (notes == null || notes.isEmpty()) {
            Toast.makeText(activity, "Select at least 1 note to create a stack widget.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasPublic = false;
        for (GistNote note : notes) {
            if (note.isPublic) {
                hasPublic = true;
                break;
            }
        }

        if (hasPublic) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.public_widget_title)
                    .setMessage(R.string.public_widget_warning)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.add_public_widget,
                            (dialog, which) -> showWidgetGridModal(activity, notes, null, onComplete))
                    .show();
            return;
        }

        showWidgetGridModal(activity, notes, null, onComplete);
    }

    private static void showWidgetGridModal(Activity activity, List<GistNote> notes,
                                            Runnable onSelectMultiple, Runnable onComplete) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.dialog_widget_choices_grid, null);

        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView subtitleView = dialogView.findViewById(R.id.dialog_subtitle);
        ImageButton closeBtn = dialogView.findViewById(R.id.dialog_close_button);
        LinearLayout actionsContainer = dialogView.findViewById(R.id.actions_grid_container);
        LinearLayout placedContainer = dialogView.findViewById(R.id.placed_grid_container);
        TextView placedEmpty = dialogView.findViewById(R.id.placed_empty_text);

        boolean isMulti = notes.size() > 1;
        GistNote singleNote = isMulti ? null : notes.get(0);

        if (isMulti) {
            titleView.setText("Create Stack Widget");
            subtitleView.setText(notes.size() + " notes selected");
        } else {
            titleView.setText("Add to Home Screen");
            String displayTitle = (singleNote != null && singleNote.title != null && !singleNote.title.isEmpty())
                    ? singleNote.title : "Note";
            subtitleView.setText(displayTitle);
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().getAttributes().setBlurBehindRadius(32);
            }
        }

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        // 1. Action Cards
        List<View> actionCards = new ArrayList<>();

        // Card: Add to Home Screen (Stack Widget by default)
        String stackSubtitle = isMulti ? "Stack " + notes.size() + " notes on home screen" : "Add to home screen (can add notes later)";
        String stackTitle = isMulti ? "Create Note Stack" : "Add to Home Screen";
        actionCards.add(createChoiceCard(inflater, actionsContainer,
                R.drawable.ic_layers,
                0xFF008F83,
                isMulti ? "STACK (" + notes.size() + ")" : "STACK",
                stackTitle,
                stackSubtitle,
                true,
                v -> {
                    dialog.dismiss();
                    requestNewStackWithNotes(activity, notes, onComplete);
                }));

        if (!isMulti && singleNote != null) {
            if (onSelectMultiple != null) {
                // Card: Select Multiple Notes
                actionCards.add(createChoiceCard(inflater, actionsContainer,
                        R.drawable.ic_check_circle_selected,
                        0xFF008F83,
                        "MULTI",
                        "Select Notes",
                        "Select multiple notes to stack",
                        false,
                        v -> {
                            dialog.dismiss();
                            onSelectMultiple.run();
                        }));
            }
        }

        populate2ColumnGrid(activity, actionsContainer, actionCards);

        // 2. Placed Widgets (Widgets placed on home screen)
        NoteStore store = new NoteStore(activity);
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        int[] activeStackIds = manager.getAppWidgetIds(new ComponentName(activity, StackNoteWidgetProvider.class));
        int[] activeSingleIds = manager.getAppWidgetIds(new ComponentName(activity, NoteWidgetProvider.class));

        List<View> placedCards = new ArrayList<>();

        // Stack Widgets on Screen
        for (int stackId : activeStackIds) {
            NoteStore.StackWidgetBinding stack = store.getStack(stackId);
            int count = stack != null ? stack.notes.size() : 0;
            String cardSubtitle = count + " note" + (count == 1 ? "" : "s") + " · Tap to add";
            placedCards.add(createChoiceCard(inflater, placedContainer,
                    R.drawable.ic_layers,
                    activity.getColor(R.color.home_text),
                    "STACK #" + stackId,
                    "Stack #" + stackId,
                    cardSubtitle,
                    false,
                    v -> {
                        dialog.dismiss();
                        addMultipleToStack(activity, notes, stackId, onComplete);
                    }));
        }

        // Single Note Widgets on Screen (only show when adding 1 note)
        if (!isMulti && singleNote != null) {
            for (int widgetId : activeSingleIds) {
                NoteStore.WidgetBinding single = store.getWidget(widgetId);
                String widgetTitle = single != null && single.filename != null ? single.filename : "Widget #" + widgetId;
                placedCards.add(createChoiceCard(inflater, placedContainer,
                        R.drawable.ic_pin,
                        activity.getColor(R.color.home_muted),
                        "NOTE #" + widgetId,
                        widgetTitle,
                        "Replace with this note",
                        false,
                        v -> {
                            dialog.dismiss();
                            replaceSingleWidget(activity, singleNote, widgetId, onComplete);
                        }));
            }
        }

        if (placedCards.isEmpty()) {
            placedContainer.setVisibility(View.GONE);
            placedEmpty.setVisibility(View.VISIBLE);
        } else {
            placedContainer.setVisibility(View.VISIBLE);
            placedEmpty.setVisibility(View.GONE);
            populate2ColumnGrid(activity, placedContainer, placedCards);
        }

        dialog.show();
    }

    private static View createChoiceCard(LayoutInflater inflater, ViewGroup parent,
                                         int iconRes, int iconTint,
                                         String badgeText,
                                         String title, String subtitle,
                                         boolean isAccent,
                                         View.OnClickListener onClick) {
        View card = inflater.inflate(R.layout.item_widget_choice_grid_card, parent, false);
        if (isAccent) {
            card.setBackgroundResource(R.drawable.widget_choice_card_accent_bg);
        }
        ImageView icon = card.findViewById(R.id.choice_card_icon);
        icon.setImageResource(iconRes);
        if (iconTint != 0) icon.setColorFilter(iconTint);

        TextView badge = card.findViewById(R.id.choice_card_badge);
        if (badgeText != null && !badgeText.isEmpty()) {
            badge.setText(badgeText);
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }

        TextView titleView = card.findViewById(R.id.choice_card_title);
        titleView.setText(title);

        TextView subView = card.findViewById(R.id.choice_card_subtitle);
        subView.setText(subtitle);

        card.setOnClickListener(onClick);
        return card;
    }

    private static void populate2ColumnGrid(Context context, LinearLayout container, List<View> cards) {
        container.removeAllViews();
        int margin = Math.round(context.getResources().getDisplayMetrics().density * 8);
        for (int i = 0; i < cards.size(); i += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = margin;
            row.setLayoutParams(rowParams);

            View card1 = cards.get(i);
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            card1.setLayoutParams(p1);
            row.addView(card1);

            if (i + 1 < cards.size()) {
                p1.rightMargin = margin;
                View card2 = cards.get(i + 1);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                card2.setLayoutParams(p2);
                row.addView(card2);
            } else {
                p1.rightMargin = margin;
                View spacer = new View(context);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                spacer.setLayoutParams(p2);
                row.addView(spacer);
            }
            container.addView(row);
        }
    }

    private static void requestConfirmed(Activity activity, GistNote note) {
        requestNewStackWithNotes(activity, Collections.singletonList(note), null);
    }

    private static void requestNewStackWithNotes(Activity activity, List<GistNote> notes, Runnable onComplete) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(activity, "Open the widget picker and choose GistNote Stack.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        ComponentName provider = new ComponentName(activity, StackNoteWidgetProvider.class);
        if (!manager.isRequestPinAppWidgetSupported()) {
            Toast.makeText(activity, "This launcher does not support in-app widget pinning. Use its widget picker.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        NoteStore store = new NoteStore(activity);
        ArrayList<String> gistIds = new ArrayList<>();
        for (GistNote note : notes) {
            gistIds.add(note.id);
            if (note.content != null && !note.content.isEmpty()) {
                store.putRemote(note);
            }
        }
        store.setPendingStackNotes(gistIds);

        Intent callback = new Intent(activity, PinWidgetReceiver.class)
                .setAction(PinWidgetReceiver.ACTION_STACK_PINNED)
                .putStringArrayListExtra("gist_ids", gistIds);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent success = PendingIntent.getBroadcast(
                activity, (int) System.currentTimeMillis(), callback, flags);
        boolean requested = manager.requestPinAppWidget(provider, null, success);
        Toast.makeText(activity, requested
                ? "Confirm the Stack Widget with your launcher."
                : "Use the launcher’s widget picker to add GistNote Stack.", Toast.LENGTH_LONG).show();
        if (onComplete != null) onComplete.run();
    }

    private static void addToStack(Context context, GistNote note, int widgetId) {
        addMultipleToStack(context, Collections.singletonList(note), widgetId, null);
    }

    private static void addMultipleToStack(Context context, List<GistNote> notes, int widgetId, Runnable onComplete) {
        NoteStore store = new NoteStore(context);
        NoteStore.StackWidgetBinding stack = store.getStack(widgetId);
        if (stack == null) {
            Toast.makeText(context, "That stack is no longer available.", Toast.LENGTH_SHORT).show();
            return;
        }
        int added = 0;
        for (GistNote note : notes) {
            if (!stack.contains(note.id)) {
                if (note.content != null && !note.content.isEmpty()) store.putRemote(note);
                stack = stack.withAdded(note);
                added++;
            }
        }
        store.bindStack(widgetId, stack);
        StackNoteWidgetProvider.update(context, AppWidgetManager.getInstance(context), widgetId);
        Toast.makeText(context, added > 0
                ? "Added " + added + " note" + (added == 1 ? "" : "s") + " to stack widget #" + widgetId + "."
                : "Selected notes were already in that stack.", Toast.LENGTH_SHORT).show();
        if (onComplete != null) onComplete.run();
    }

    private static void replaceSingleWidget(Context context, GistNote note, int widgetId, Runnable onComplete) {
        NoteStore store = new NoteStore(context);
        NoteStore.WidgetBinding existing = store.getWidget(widgetId);
        String color = existing != null ? existing.color : "Transparent";
        String textColor = existing != null ? existing.textColor : "White";
        String textSize = existing != null ? existing.textSize : "Medium";
        String alignment = existing != null ? existing.alignment : "start";
        if (note.content != null && !note.content.isEmpty()) store.putRemote(note);
        store.bindWidget(widgetId, new NoteStore.WidgetBinding(note.id, note.filename, color, textColor, textSize, alignment));
        NoteWidgetProvider.update(context, AppWidgetManager.getInstance(context), widgetId);
        Toast.makeText(context, "Updated single widget #" + widgetId + ".", Toast.LENGTH_SHORT).show();
        if (onComplete != null) onComplete.run();
    }
}
