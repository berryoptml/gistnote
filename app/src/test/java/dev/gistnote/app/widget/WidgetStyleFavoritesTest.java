package dev.gistnote.app.widget;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class WidgetStyleFavoritesTest {
    @Test public void unsetAndMalformedFavoritesUseDefaults() {
        assertEquals(123, WidgetStyleFavorites.parseFavorite("", 123));
        assertEquals(123, WidgetStyleFavorites.parseFavorite(null, 123));
        assertEquals(123, WidgetStyleFavorites.parseFavorite("#notrgb", 123));
    }
    @Test public void favoriteRgbIsOpaque() {
        assertEquals(0xFF8BE0C5, WidgetStyleFavorites.parseFavorite("#8be0c5", 0));
    }
}
