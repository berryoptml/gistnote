package dev.gistnote.app.ui;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

/** Full hue field with white/black vertical shading and a separate alpha track. */
final class RectangularColorPicker extends LinearLayout {
    private final float[] hsv = new float[3];
    private int alpha;
    private float fieldY;
    private final DetailedColorPickerDialog.Selection selection;

    RectangularColorPicker(Context context, int color, DetailedColorPickerDialog.Selection selection) {
        super(context);
        this.selection = selection;
        Color.colorToHSV(color, hsv);
        alpha = Color.alpha(color);
        fieldY = hsv[2] < 1f ? 1f - hsv[2] / 2f : hsv[1] / 2f;
        setOrientation(HORIZONTAL);
        View field = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth(), h = getHeight();
                Path clip = new Path();
                clip.addRoundRect(0, 0, w, h, dp(8), dp(8), Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                paint.setShader(new LinearGradient(0, 0, w, 0,
                        new int[] {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED},
                        null, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[] {Color.WHITE, Color.TRANSPARENT, Color.BLACK},
                        new float[] {0, .5f, 1}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
                float x = hsv[0] / 360f * w, y = fieldY * h;
                // A small selection marker, without an outline around the color field.
                paint.setColor(Color.WHITE);
                canvas.drawCircle(Math.max(dp(5), Math.min(w - dp(5), x)),
                        Math.max(dp(5), Math.min(h - dp(5), y)), dp(4), paint);
                canvas.restore();
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    hsv[0] = Math.min(359.99f, clamp(event.getX() / getWidth()) * 360f);
                    fieldY = clamp(event.getY() / getHeight());
                    hsv[1] = Math.min(1f, fieldY * 2f);
                    hsv[2] = Math.min(1f, (1f - fieldY) * 2f);
                    selection.selected(Color.HSVToColor(alpha, hsv));
                    invalidate();
                }
                if (event.getAction() == MotionEvent.ACTION_UP) performClick();
                return true;
            }
            @Override public boolean performClick() { super.performClick(); return true; }
        };
        field.setContentDescription("Color field: hue horizontally, white to black vertically");
        addView(field, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        View opacity = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth(), h = getHeight();
                paint.setColor(0x30808080);
                canvas.drawRoundRect(0, 0, w, h, dp(7), dp(7), paint);
                paint.setColor(Color.WHITE);
                float top = h * (1f - alpha / 255f);
                if (top < h) canvas.drawRoundRect(0, top, w, h, dp(7), dp(7), paint);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    alpha = Math.round(255 * (1f - clamp(event.getY() / getHeight())));
                    selection.selected(Color.HSVToColor(alpha, hsv));
                    invalidate();
                }
                if (event.getAction() == MotionEvent.ACTION_UP) performClick();
                return true;
            }
            @Override public boolean performClick() { super.performClick(); return true; }
        };
        opacity.setContentDescription("Alpha: top opaque, bottom transparent");
        LayoutParams alphaParams = new LayoutParams(dp(26), LayoutParams.MATCH_PARENT);
        alphaParams.leftMargin = dp(12);
        addView(opacity, alphaParams);
    }
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
