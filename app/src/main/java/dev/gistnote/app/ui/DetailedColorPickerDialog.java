package dev.gistnote.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class DetailedColorPickerDialog {
    public interface Selection { void selected(int color); }

    private DetailedColorPickerDialog() { }

    /** Compact live picker anchored below the widget appearance window. */
    public static void showBelow(Activity activity, String label, int initialColor, Selection selection) {
        android.app.Dialog dialog = new android.app.Dialog(activity, dev.gistnote.app.R.style.Theme_GistNote_Appearance);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 6));
        RectangularColorPicker picker = new RectangularColorPicker(activity, initialColor, selection);
        picker.setContentDescription(label);
        content.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 190)));
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);
        android.view.Window window = dialog.getWindow();
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setCornerRadius(dp(activity, 18));
        window.setBackgroundDrawable(surface);
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.show();
        android.view.WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        attributes.y = activity.getWindow().getAttributes().y
                + activity.getWindow().getDecorView().getHeight() + dp(activity, 8);
        attributes.dimAmount = 0;
        window.setAttributes(attributes);
        window.setLayout(activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        Runnable updateBlur = () -> {
            boolean supported = android.os.Build.VERSION.SDK_INT >= 31
                    && activity.getWindowManager().isCrossWindowBlurEnabled();
            int radius = activity.getSharedPreferences("widget_style", Activity.MODE_PRIVATE).getInt("panel_blur", 36);
            int color = activity.getColor(dev.gistnote.app.R.color.surface);
            surface.setColor((color & 0xFFFFFF) | ((supported && radius > 0 ? 205 : 255) << 24));
            if (android.os.Build.VERSION.SDK_INT >= 31)
                window.setBackgroundBlurRadius(supported ? dp(activity, radius) : 0);
        };
        updateBlur.run();
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            java.util.function.Consumer<Boolean> listener = enabled -> updateBlur.run();
            activity.getWindowManager().addCrossWindowBlurEnabledListener(listener);
            dialog.setOnDismissListener(ignored ->
                    activity.getWindowManager().removeCrossWindowBlurEnabledListener(listener));
        }
    }

    public static void show(Activity activity, String label, int initialColor, Selection selection) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 8));

        TextView heading = new TextView(activity);
        heading.setText(label);
        heading.setGravity(Gravity.CENTER);
        heading.setTextColor(0xFF008F83);
        heading.setTextSize(14);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(heading);

        TextView hex = new TextView(activity);
        hex.setGravity(Gravity.CENTER);
        hex.setTextColor(0xFF008F83);
        hex.setTextSize(26);
        hex.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        content.addView(hex, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 58)));

        ColorWheelView wheel = new ColorWheelView(activity);
        wheel.setColor(0xFF000000 | (initialColor & 0x00FFFFFF));
        content.addView(wheel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 330)));

        LinearLayout brightnessRow = new LinearLayout(activity);
        brightnessRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brightnessLabel = new TextView(activity);
        brightnessLabel.setText("Brightness");
        brightnessLabel.setTextColor(activity.getColor(dev.gistnote.app.R.color.ink));
        brightnessRow.addView(brightnessLabel, new LinearLayout.LayoutParams(
                dp(activity, 86), LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar brightness = new SeekBar(activity);
        brightness.setMax(100);
        float[] hsv = new float[3];
        Color.colorToHSV(initialColor, hsv);
        brightness.setProgress(Math.round(hsv[2] * 100f));
        brightnessRow.addView(brightness, new LinearLayout.LayoutParams(
                0, dp(activity, 48), 1f));
        content.addView(brightnessRow);

        Runnable updateHex = () -> hex.setText(String.format(Locale.ROOT,
                "#%06X", 0xFFFFFF & wheel.getColor()));
        wheel.setListener(color -> updateHex.run());
        updateHex.run();
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) wheel.setValue(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        new AlertDialog.Builder(activity)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> selection.selected(wheel.getColor()))
                .show();
    }

    public static void styleSlider(Activity activity, SeekBar slider) {
        android.graphics.drawable.GradientDrawable track = new android.graphics.drawable.GradientDrawable();
        track.setColor(0x30808080);
        track.setCornerRadius(dp(activity, 6));
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(Color.WHITE);
        float r = dp(activity, 6);
        fill.setCornerRadii(new float[] {r, r, 0f, 0f, 0f, 0f, r, r});
        android.graphics.drawable.ScaleDrawable progress = new android.graphics.drawable.ScaleDrawable(
                fill, Gravity.START, 1f, -1f);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[] {track, progress});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        for (int i = 0; i < 2; i++) {
            layers.setLayerHeight(i, dp(activity, 20));
            layers.setLayerGravity(i, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        }
        slider.setProgressDrawable(layers);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        android.graphics.drawable.GradientDrawable marker = new android.graphics.drawable.GradientDrawable();
        marker.setColor(0xFF777777);
        marker.setCornerRadius(dp(activity, 1));
        marker.setSize(dp(activity, 2), dp(activity, 20));
        slider.setThumbTintList(null);
        slider.setThumb(marker);
        slider.setThumbOffset(dp(activity, 1));
        slider.setSplitTrack(false);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
