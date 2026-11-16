package com.example.wtfff.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PixelBranchConnectorView extends View {
    private final Paint railPaint = new Paint();
    private final Paint nodePaint = new Paint();
    private int depth = 0;
    private int accentColor = 0xFF00F0FF;

    public PixelBranchConnectorView(Context context) {
        super(context);
        init();
    }

    public PixelBranchConnectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PixelBranchConnectorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        railPaint.setAntiAlias(false);
        railPaint.setStyle(Paint.Style.FILL);
        nodePaint.setAntiAlias(false);
        nodePaint.setStyle(Paint.Style.FILL);
    }

    public void setBranchSpec(int depth, int accentColor) {
        this.depth = Math.max(0, Math.min(4, depth));
        this.accentColor = accentColor;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int indentWidth = depth == 0 ? 0 : Math.round((depth * 18 + 6) * density);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(indentWidth, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (depth <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        int stroke = Math.max(2, Math.round(2 * density));
        int step = Math.round(18 * density);
        int h = getHeight();
        int w = getWidth();

        // Faint ancestor rails
        railPaint.setColor(0x443B5284);
        for (int i = 0; i < depth - 1; i++) {
            int x = i * step + Math.round(8 * density);
            canvas.drawRect(x, 0, x + stroke, h, railPaint);
        }

        // Active parent elbow rail in neon accent
        int activeX = (depth - 1) * step + Math.round(8 * density);
        int midY = Math.round(24 * density);

        railPaint.setColor((accentColor & 0x00FFFFFF) | 0xAA000000);
        // Vertical drop
        canvas.drawRect(activeX, 0, activeX + stroke, midY + stroke, railPaint);
        // 90-degree horizontal arm to cartridge
        canvas.drawRect(activeX, midY, w, midY + stroke, railPaint);

        // Crisp 6x6 pixel solder joint node
        nodePaint.setColor(accentColor);
        int nodeHalf = Math.max(3, Math.round(3 * density));
        canvas.drawRect(
                activeX - nodeHalf + stroke / 2,
                midY - nodeHalf + stroke / 2,
                activeX + nodeHalf + stroke / 2,
                midY + nodeHalf + stroke / 2,
                nodePaint
        );
    }
}
