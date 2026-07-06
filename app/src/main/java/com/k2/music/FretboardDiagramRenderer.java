package com.k2.music;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public final class FretboardDiagramRenderer {
    private static final int STRING_COUNT = 6;

    private FretboardDiagramRenderer() {
    }

    public static void draw(Canvas canvas, RectF bounds, Voicing voicing, float density, boolean framed) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (framed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(bounds, dp(density, 18), dp(density, 18), paint);
        }

        if (voicing == null) {
            drawEmpty(canvas, bounds, density, paint);
            return;
        }

        float left = bounds.left + dp(density, 44);
        float right = bounds.right - dp(density, 32);
        float top = bounds.top + dp(density, 62);
        float bottom = bounds.bottom - dp(density, 34);
        float stringGap = (right - left) / (STRING_COUNT - 1);
        float fretGap = (bottom - top) / voicing.displayFrets;

        drawFrets(canvas, voicing, left, right, top, fretGap, density, paint);
        drawStrings(canvas, left, top, bottom, stringGap, density, paint);
        drawOpenAndMutedMarks(canvas, voicing, left, top, stringGap, density, paint);
        drawFingerMarkers(canvas, voicing, left, top, fretGap, stringGap, density, paint);
        drawStartFret(canvas, voicing, left, top, fretGap, density, paint);
    }

    private static void drawEmpty(Canvas canvas, RectF bounds, float density, Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFB4BBB6);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(dp(density, 18));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText("请输入和弦名称", bounds.centerX(), y, paint);
    }

    private static void drawFrets(
            Canvas canvas,
            Voicing voicing,
            float left,
            float right,
            float top,
            float fretGap,
            float density,
            Paint paint
    ) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i <= voicing.displayFrets; i++) {
            float y = top + i * fretGap;
            paint.setColor(i == 0 && voicing.startFret == 1 ? 0xFF101412 : 0xFF6E7771);
            paint.setStrokeWidth(i == 0 && voicing.startFret == 1 ? dp(density, 3) : dp(density, 1));
            canvas.drawLine(left, y, right, y, paint);
        }
    }

    private static void drawStrings(
            Canvas canvas,
            float left,
            float top,
            float bottom,
            float stringGap,
            float density,
            Paint paint
    ) {
        paint.setColor(0xFF56615B);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < STRING_COUNT; i++) {
            float x = left + i * stringGap;
            paint.setStrokeWidth(dp(density, 1) + i * dp(density, 0.16f));
            canvas.drawLine(x, top, x, bottom, paint);
        }
    }

    private static void drawOpenAndMutedMarks(
            Canvas canvas,
            Voicing voicing,
            float left,
            float top,
            float stringGap,
            float density,
            Paint paint
    ) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(density, 18));
        paint.setFakeBoldText(true);
        for (int i = 0; i < STRING_COUNT; i++) {
            float x = left + i * stringGap;
            int fret = voicing.frets[i];
            if (fret == Voicing.MUTED) {
                drawMuted(canvas, x, top - dp(density, 26), density, paint);
            } else if (fret == 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF101412);
                canvas.drawText("O", x, top - dp(density, 18), paint);
            }
        }
    }

    private static void drawFingerMarkers(
            Canvas canvas,
            Voicing voicing,
            float left,
            float top,
            float fretGap,
            float stringGap,
            float density,
            Paint paint
    ) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(density, 16));
        paint.setFakeBoldText(true);
        for (int i = 0; i < STRING_COUNT; i++) {
            int fret = voicing.frets[i];
            if (fret <= 0) {
                continue;
            }
            float visibleFret = fret - voicing.startFret + 1;
            if (visibleFret < 1 || visibleFret > voicing.displayFrets) {
                continue;
            }
            float x = left + i * stringGap;
            float y = top + (visibleFret - 0.5f) * fretGap;
            float radius = Math.min(dp(density, 22), fretGap * 0.32f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF101412);
            canvas.drawCircle(x, y, radius, paint);

            int finger = voicing.fingers[i];
            if (finger > 0) {
                paint.setColor(0xFFFFFFFF);
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float textY = y - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(String.valueOf(finger), x, textY, paint);
            }
        }
    }

    private static void drawStartFret(
            Canvas canvas,
            Voicing voicing,
            float left,
            float top,
            float fretGap,
            float density,
            Paint paint
    ) {
        if (voicing.startFret <= 1) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF101412);
        paint.setTextSize(dp(density, 14));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setFakeBoldText(true);
        canvas.drawText(voicing.startFret + "fr", left - dp(density, 8), top + fretGap * 0.65f, paint);
    }

    private static void drawMuted(Canvas canvas, float x, float y, float density, Paint paint) {
        float size = dp(density, 7);
        paint.setColor(0xFF111513);
        paint.setStrokeWidth(dp(density, 2));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x - size, y - size, x + size, y + size, paint);
        canvas.drawLine(x + size, y - size, x - size, y + size, paint);
    }

    private static float dp(float density, float value) {
        return value * density;
    }
}
