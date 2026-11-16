package com.example.wtfff.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PixelGridBackgroundView extends View {
    private final Paint voidPaint = new Paint();
    private final Paint gridLinePaint = new Paint();
    private final Paint dotPaint = new Paint();
    private final Paint scanlinePaint = new Paint();

    public PixelGridBackgroundView(Context context) {
        super(context);
        init();
    }

    public PixelGridBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PixelGridBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        voidPaint.setColor(0xFF080A12);
        voidPaint.setStyle(Paint.Style.FILL);

        gridLinePaint.setColor(0x1400F0FF);
        gridLinePaint.setStrokeWidth(1f);

        dotPaint.setColor(0x3000F0FF);
        dotPaint.setStyle(Paint.Style.FILL);

        scanlinePaint.setColor(0x18000000);
        scanlinePaint.setStrokeWidth(2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, voidPaint);

        float density = getResources().getDisplayMetrics().density;
        int step = Math.max(24, Math.round(22 * density));
        int dotSize = Math.max(2, Math.round(2 * density));

        for (int x = step / 2; x < w; x += step) {
            canvas.drawLine(x, 0, x, h, gridLinePaint);
        }
        for (int y = step / 2; y < h; y += step) {
            canvas.drawLine(0, y, w, y, gridLinePaint);
            for (int x = step / 2; x < w; x += step) {
                canvas.drawRect(x - 1, y - 1, x + dotSize - 1, y + dotSize - 1, dotPaint);
            }
        }

        int scanStep = Math.max(6, Math.round(4 * density));
        for (int y = 0; y < h; y += scanStep) {
            canvas.drawLine(0, y, w, y, scanlinePaint);
        }
    }
}
