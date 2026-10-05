package dev.gistnote.app.ui;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

/** Attaches touch-and-hold tooltips to views with descriptions or tooltip attributes. */
public final class TooltipHelper {
    private TooltipHelper() { }

    public static void setTooltip(View view, CharSequence text) {
        if (view == null || text == null || text.length() == 0) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setTooltipText(text);
        }
        view.setOnLongClickListener(v -> {
            Toast.makeText(v.getContext(), text, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    public static void applyTooltips(View root) {
        if (root == null) return;
        CharSequence desc = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            desc = root.getTooltipText();
        }
        if (desc == null || desc.length() == 0) {
            desc = root.getContentDescription();
        }
        if (desc != null && desc.length() > 0) {
            setTooltip(root, desc);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTooltips(group.getChildAt(i));
            }
        }
    }
}

