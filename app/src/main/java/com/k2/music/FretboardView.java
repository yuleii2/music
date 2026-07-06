package com.k2.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class FretboardView extends View {
    private Voicing voicing;

    public FretboardView(Context context) {
        super(context);
        init();
    }

    public FretboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setVoicing(Voicing voicing) {
        this.voicing = voicing;
        invalidate();
    }

    private void init() {
        setMinimumHeight(dp(320));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        FretboardDiagramRenderer.draw(
                canvas,
                new RectF(0, 0, getWidth(), getHeight()),
                voicing,
                getResources().getDisplayMetrics().density,
                true
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
