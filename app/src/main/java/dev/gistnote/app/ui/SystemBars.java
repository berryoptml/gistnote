package dev.gistnote.app.ui;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

public final class SystemBars {
    private SystemBars() { }

    @SuppressWarnings("deprecation")
    public static void apply(View root) {
        apply(root, false);
    }

    @SuppressWarnings("deprecation")
    public static void applyWithIme(View root) {
        apply(root, true);
    }

    @SuppressWarnings("deprecation")
    private static void apply(View root, boolean includeIme) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int insetLeft;
            int insetTop;
            int insetRight;
            int insetBottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = windowInsets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                insetLeft = bars.left;
                insetTop = bars.top;
                insetRight = bars.right;
                insetBottom = bars.bottom;
                if (includeIme) {
                    Insets ime = windowInsets.getInsets(WindowInsets.Type.ime());
                    insetBottom = Math.max(insetBottom, ime.bottom);
                }
            } else {
                insetLeft = windowInsets.getSystemWindowInsetLeft();
                insetTop = windowInsets.getSystemWindowInsetTop();
                insetRight = windowInsets.getSystemWindowInsetRight();
                insetBottom = windowInsets.getSystemWindowInsetBottom();
            }
            view.setPadding(left + insetLeft, top + insetTop,
                    right + insetRight, bottom + insetBottom);
            return windowInsets;
        });
        root.requestApplyInsets();
    }
}
