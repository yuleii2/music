package com.k2.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class ChordPreviewView extends View {
    private static final int STRING_COUNT = 6;
    private Voicing voicing;

    public ChordPreviewView(Context context) {
        super(context);
    }

    public void setVoicing(Voicing voicing) {
        this.voicing = voicing;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (voicing == null) {
            drawEmpty(canvas, paint);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float left = dp(density, 25);
        float right = getWidth() - dp(density, 8);
        float top = dp(density, 24);
        float bottom = getHeight() - dp(density, 8);
        float stringGap = (right - left) / (STRING_COUNT - 1);
        float fretGap = (bottom - top) / voicing.displayFrets;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int fret = 0; fret <= voicing.displayFrets; fret++) {
            float y = top + fret * fretGap;
            paint.setColor(fret == 0 && voicing.startFret == 1 ? 0xFF101412 : 0xFFA9B0AB);
            paint.setStrokeWidth(fret == 0 && voicing.startFret == 1 ? dp(density, 2.2f) : dp(density, 1));
            canvas.drawLine(left, y, right, y, paint);
        }

        paint.setColor(0xFF9AA19C);
        for (int string = 0; string < STRING_COUNT; string++) {
            float x = left + string * stringGap;
            paint.setStrokeWidth(dp(density, 1));
            canvas.drawLine(x, top, x, bottom, paint);
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(density, 11));
        paint.setFakeBoldText(true);
        for (int string = 0; string < STRING_COUNT; string++) {
            float x = left + string * stringGap;
            int fret = voicing.frets[string];
            if (fret == Voicing.MUTED) {
                drawMuted(canvas, paint, x, top - dp(density, 12), density);
            } else if (fret == 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF101412);
                canvas.drawText("O", x, top - dp(density, 8), paint);
            }
        }

        paint.setTextSize(dp(density, 10));
        for (int string = 0; string < STRING_COUNT; string++) {
            int fret = voicing.frets[string];
            if (fret <= 0) {
                continue;
            }
            float visibleFret = fret - voicing.startFret + 1;
            if (visibleFret < 1 || visibleFret > voicing.displayFrets) {
                continue;
            }
            float x = left + string * stringGap;
            float y = top + (visibleFret - 0.5f) * fretGap;
            float radius = Math.min(dp(density, 11), fretGap * 0.32f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF101412);
            canvas.drawCircle(x, y, radius, paint);

            int finger = voicing.fingers[string];
            if (finger > 0) {
                paint.setColor(0xFFFFFFFF);
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float textY = y - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(String.valueOf(finger), x, textY, paint);
            }
        }

        if (voicing.startFret > 1) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(density, 10));
            paint.setColor(0xFF101412);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.valueOf(voicing.startFret), left - dp(density, 7), top + fretGap * 0.62f, paint);
        }
    }

    private void drawEmpty(Canvas canvas, Paint paint) {
        paint.setColor(0xFF9AA19C);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(getResources().getDisplayMetrics().density, 13));
        canvas.drawText("暂无指法", getWidth() / 2f, getHeight() / 2f, paint);
    }

    private void drawMuted(Canvas canvas, Paint paint, float x, float y, float density) {
        float size = dp(density, 4.5f);
        paint.setColor(0xFF101412);
        paint.setStrokeWidth(dp(density, 1.6f));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x - size, y - size, x + size, y + size, paint);
        canvas.drawLine(x + size, y - size, x - size, y + size, paint);
    }

    private static float dp(float density, float value) {
        return value * density;
    }
}
