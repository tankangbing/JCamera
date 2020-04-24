package pony.xcode.jcamera;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import pony.xcode.jcamera.util.ScreenUtils;


public class FocusView extends View {
    private int size;
    private int center_x;
    private int center_y;
    private int length;
    private Paint mPaint;

    public FocusView(Context context) {
        this(context, null);
    }

    public FocusView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FocusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.size = ScreenUtils.getScreenWidth(context) / 3;
        int color = 0xEE16AE16;
        int strokeWidth = 4;
        Resources.Theme theme = context.getTheme();
        if (theme != null) {
            TypedValue typedValue = new TypedValue();
            if (theme.resolveAttribute(R.attr.jc_focus_size, typedValue, true)) {
                this.size = context.getResources().getDimensionPixelSize(typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_focus_color, typedValue, true)) {
                color = ContextCompat.getColor(context, typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_focus_width, typedValue, true)) {
                strokeWidth = context.getResources().getDimensionPixelSize(typedValue.resourceId);
            }
        }
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true); //颤动
        mPaint.setColor(color);
        mPaint.setStrokeWidth(strokeWidth);
        mPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        center_x = (int) (size / 2.0);
        center_y = (int) (size / 2.0);
        length = (int) (size / 2.0) - 2;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(center_x - length, center_y - length, center_x + length, center_y + length, mPaint);
        canvas.drawLine(2, getHeight() / 2f, size / 10f, getHeight() / 2f, mPaint);
        canvas.drawLine(getWidth() - 2, getHeight() / 2f, getWidth() - size / 10f, getHeight() / 2f, mPaint);
        canvas.drawLine(getWidth() / 2f, 2, getWidth() / 2f, size / 10f, mPaint);
        canvas.drawLine(getWidth() / 2f, getHeight() - 2, getWidth() / 2f, getHeight() - size / 10f, mPaint);
    }
}
