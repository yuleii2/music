package com.k2.music;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public final class FretboardDiagramRenderer {
    private static final int STRING_COUNT = 6;

    private FretboardDiagramRenderer() {
    }

    public static void draw(Canvas canvas, RectF bounds, Voicing voicing, float density, boolean framed) {
        draw(canvas, bounds, voicing, density, framed, new Paint(Paint.ANTI_ALIAS_FLAG));
    }

    public static void draw(Canvas canvas, RectF bounds, Voicing voicing, float density, boolean framed, Paint paint) {
        paint.reset();
        paint.setAntiAlias(true);
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
        float bottom = bounds.bottom - dp(density, 30);
        float stringGap = (right - left) / (STRING_COUNT - 1);
        int visibleFrets = Math.max(5, voicing.displayFrets);
        float fretGap = (bottom - top) / visibleFrets;

        drawFretNumbers(canvas, voicing, left, top, fretGap, visibleFrets, density, paint);
        drawFrets(canvas, voicing, left, right, top, fretGap, visibleFrets, density, paint);
        drawStrings(canvas, left, top, bottom, stringGap, density, paint);
        drawOpenAndMutedMarks(canvas, voicing, left, top, stringGap, density, paint);
        drawBarres(canvas, voicing, left, top, fretGap, stringGap, visibleFrets, density, paint);
        drawFingerMarkers(canvas, voicing, left, top, fretGap, stringGap, visibleFrets, density, paint);
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
            int visibleFrets,
            float density,
            Paint paint
    ) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i <= visibleFrets; i++) {
            float y = top + i * fretGap;
            paint.setColor(i == 0 && voicing.startFret == 1 ? 0xFF111111 : 0xFFD2D2D2);
            paint.setStrokeWidth(i == 0 && voicing.startFret == 1 ? dp(density, 2.4f) : dp(density, 0.9f));
            canvas.drawLine(left, y, right, y, paint);
        }
    }

    private static void drawFretNumbers(
            Canvas canvas,
            Voicing voicing,
            float left,
            float top,
            float fretGap,
            int visibleFrets,
            float density,
            Paint paint
    ) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF444444);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setFakeBoldText(false);
        paint.setTextSize(dp(density, 13));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        for (int fret = 1; fret <= visibleFrets; fret++) {
            float y = top + (fret - 0.5f) * fretGap - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(voicing.startFret + fret - 1), left - dp(density, 13), y, paint);
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
        paint.setColor(0xFFC6C6C6);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < STRING_COUNT; i++) {
            float x = left + i * stringGap;
            paint.setStrokeWidth(dp(density, 0.9f));
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
                paint.setColor(0xFF111111);
                canvas.drawText("o", x, top - dp(density, 18), paint);
            }
        }
    }

    private static void drawBarres(
            Canvas canvas,
            Voicing voicing,
            float left,
            float top,
            float fretGap,
            float stringGap,
            int visibleFrets,
            float density,
            Paint paint
    ) {
        for (int fret = voicing.startFret; fret < voicing.startFret + visibleFrets; fret++) {
            int start = -1;
            int end = -1;
            for (int string = 0; string < STRING_COUNT; string++) {
                if (voicing.frets[string] == fret && voicing.fingers[string] == 1) {
                    if (start < 0) {
                        start = string;
                    }
                    end = string;
                }
            }
            if (start >= 0 && end > start) {
                float visibleFret = fret - voicing.startFret + 1;
                float y = top + (visibleFret - 0.5f) * fretGap;
                float x1 = left + start * stringGap;
                float x2 = left + end * stringGap;
                float height = Math.min(dp(density, 28), fretGap * 0.48f);
                RectF rect = new RectF(x1 - height * 0.5f, y - height * 0.5f, x2 + height * 0.5f, y + height * 0.5f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF111111);
                canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setFakeBoldText(true);
                paint.setTextSize(dp(density, 15));
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float textY = y - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText("1", x1, textY, paint);
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
            int visibleFrets,
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
            if (visibleFret < 1 || visibleFret > visibleFrets) {
                continue;
            }
            if (isCoveredByBarre(voicing, i)) {
                continue;
            }
            float x = left + i * stringGap;
            float y = top + (visibleFret - 0.5f) * fretGap;
            float radius = Math.min(dp(density, 17), fretGap * 0.31f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF111111);
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

    private static void drawMuted(Canvas canvas, float x, float y, float density, Paint paint) {
        paint.setColor(0xFF111111);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(dp(density, 18));
        canvas.drawText("x", x, y + dp(density, 6), paint);
    }

    private static boolean isCoveredByBarre(Voicing voicing, int stringIndex) {
        int fret = voicing.frets[stringIndex];
        if (fret <= 0 || voicing.fingers[stringIndex] != 1) {
            return false;
        }
        int count = 0;
        for (int i = 0; i < STRING_COUNT; i++) {
            if (voicing.frets[i] == fret && voicing.fingers[i] == 1) {
                count++;
            }
        }
        return count > 1;
    }

    private static float dp(float density, float value) {
        return value * density;
    }
}
