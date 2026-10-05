package dev.gistnote.app.widget;

import android.content.Context;
import android.graphics.Color;
import java.util.Locale;

/** Separate four-slot palettes; the fifth control is always a custom picker. */
public final class WidgetStyleFavorites {
    private WidgetStyleFavorites() {}
    public static int[] load(Context context, boolean background) {
        int[] colors = background
                ? new int[] {0xFF8E8D9F, Color.BLACK, Color.WHITE, 0xFF8BE0C5}
                : new int[] {Color.WHITE, Color.BLACK, 0xFFF5AB35, 0xFF8BE0C5};
        String saved = context.getSharedPreferences("widget_style", Context.MODE_PRIVATE)
                .getString(background ? "background_favorites" : "text_favorites", "");
        String[] values = saved.split(",");
        for (int i = 0; i < Math.min(4, values.length); i++) {
            colors[i] = parseFavorite(values[i], colors[i]);
        }
        return colors;
    }
    static int parseFavorite(String value, int fallback) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return fallback;
        return (int) Long.parseLong(value.substring(1), 16) | 0xFF000000;
    }
    public static void save(Context context, boolean background, int[] colors) {
        StringBuilder value = new StringBuilder();
        for (int color : colors) {
            if (value.length() > 0) value.append(',');
            value.append(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF));
        }
        context.getSharedPreferences("widget_style", Context.MODE_PRIVATE).edit()
                .putString(background ? "background_favorites" : "text_favorites", value.toString()).apply();
    }
}
