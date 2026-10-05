package dev.gistnote.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.style.ReplacementSpan;

public final class TaskCheckboxSpan extends ReplacementSpan {
    private final boolean checked;
    private final int sizePx;
    private final int marginEndPx;
    private final int color;
    private final int lineNumber;

    public TaskCheckboxSpan(boolean checked, int sizePx, int marginEndPx, int color, int lineNumber) {
        this.checked = checked;
        this.sizePx = sizePx;
        this.marginEndPx = marginEndPx;
        this.color = color;
        this.lineNumber = lineNumber;
    }

    public boolean isChecked() {
        return checked;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                       Paint.FontMetricsInt fm) {
        return sizePx + marginEndPx;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        int textCenterY = y + (fm.ascent + fm.descent) / 2;
        float boxLeft = x;
        float boxTop = textCenterY - sizePx / 2f;
        float boxRight = boxLeft + sizePx;
        float boxBottom = boxTop + sizePx;
        float cornerRadius = sizePx * 0.22f;

        Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int drawColor = color != 0 ? color : paint.getColor();
        boxPaint.setColor(drawColor);

        if (checked) {
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(sizePx * 0.11f);
            canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, cornerRadius, cornerRadius, boxPaint);

            Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkPaint.setColor(drawColor);
            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);
            checkPaint.setStrokeWidth(sizePx * 0.13f);

            Path checkPath = new Path();
            checkPath.moveTo(boxLeft + sizePx * 0.22f, boxTop + sizePx * 0.52f);
            checkPath.lineTo(boxLeft + sizePx * 0.44f, boxTop + sizePx * 0.74f);
            checkPath.lineTo(boxLeft + sizePx * 0.80f, boxTop + sizePx * 0.28f);
            canvas.drawPath(checkPath, checkPaint);
        } else {
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(sizePx * 0.11f);
            canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, cornerRadius, cornerRadius, boxPaint);
        }
    }
}
