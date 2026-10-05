package dev.gistnote.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

final class ColorWheelView extends View {
    interface Listener { void changed(int color); }

    private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float hue;
    private float saturation = 1f;
    private float value = 1f;
    private float centerX;
    private float centerY;
    private float ringRadius;
    private float discRadius;
    private float ringWidth;
    private Listener listener;

    ColorWheelView(Context context) { this(context, null); }

    ColorWheelView(Context context, AttributeSet attributes) {
        super(context, attributes);
        ringWidth = dp(28);
        huePaint.setStyle(Paint.Style.STROKE);
        huePaint.setStrokeWidth(ringWidth);
        huePaint.setStrokeCap(Paint.Cap.ROUND);
        selectorPaint.setStyle(Paint.Style.FILL);
        selectorPaint.setShadowLayer(dp(2), 0, dp(1), 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Detailed color wheel");
    }

    void setListener(Listener listener) { this.listener = listener; }

    void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        invalidate();
    }

    void setValue(float value) {
        this.value = Math.max(0f, Math.min(1f, value));
        invalidate();
        notifyChanged();
    }

    int getColor() { return Color.HSVToColor(new float[] {hue, saturation, value}); }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        centerX = width / 2f;
        centerY = height / 2f;
        ringRadius = Math.min(width, height) / 2f - ringWidth / 2f - dp(10);
        discRadius = Math.max(dp(40), ringRadius - ringWidth - dp(22));
        huePaint.setShader(new SweepGradient(centerX, centerY,
                new int[] {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                        Color.BLUE, Color.MAGENTA, Color.RED}, null));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(centerX, centerY, ringRadius, huePaint);
        int hueColor = Color.HSVToColor(new float[] {hue, 1f, 1f});
        discPaint.setShader(new RadialGradient(centerX, centerY, discRadius,
                Color.WHITE, hueColor, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, discRadius, discPaint);
        if (value < 1f) {
            discPaint.setShader(null);
            discPaint.setColor(Color.argb(Math.round((1f - value) * 255f), 0, 0, 0));
            canvas.drawCircle(centerX, centerY, discRadius, discPaint);
        }

        double hueRadians = Math.toRadians(hue);
        float hueX = centerX + (float) Math.cos(hueRadians) * ringRadius;
        float hueY = centerY + (float) Math.sin(hueRadians) * ringRadius;
        drawSelector(canvas, hueX, hueY, Color.HSVToColor(new float[] {hue, 1f, 1f}));

        float saturationX = centerX + saturation * discRadius;
        drawSelector(canvas, saturationX, centerY, getColor());
    }

    private void drawSelector(Canvas canvas, float x, float y, int color) {
        selectorPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, dp(12), selectorPaint);
        selectorPaint.setColor(color);
        canvas.drawCircle(x, y, dp(9), selectorPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN
                && event.getAction() != MotionEvent.ACTION_MOVE) return true;
        float dx = event.getX() - centerX;
        float dy = event.getY() - centerY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance >= ringRadius - ringWidth && distance <= ringRadius + ringWidth) {
            float degrees = (float) Math.toDegrees(Math.atan2(dy, dx));
            hue = degrees < 0 ? degrees + 360f : degrees;
        } else if (distance <= discRadius + dp(12)) {
            saturation = Math.max(0f, Math.min(1f, distance / discRadius));
        } else {
            return true;
        }
        invalidate();
        notifyChanged();
        return true;
    }

    private void notifyChanged() { if (listener != null) listener.changed(getColor()); }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
