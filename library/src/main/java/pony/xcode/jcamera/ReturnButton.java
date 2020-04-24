package pony.xcode.jcamera;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * 描    述：向下箭头的退出按钮
 */
public class ReturnButton extends View {

    private int mButtonSize;

    private int mCenterX;
    private int mCenterY;
    private float mStrokeWidth;

    private Paint mPaint;
    private Path mPath;

    public ReturnButton(Context context, int size) {
        this(context);
        this.mButtonSize = size;

        mStrokeWidth = mButtonSize / 15f;
        int color = JCameraConfig.BACKTRACK_COLOR;
        Resources.Theme theme = context.getTheme();
        if (theme != null) {
            TypedValue tv = new TypedValue();
            if (theme.resolveAttribute(R.attr.jc_backtrack_color, tv, true)) {
                color = ContextCompat.getColor(context, tv.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_backtrack_line_width, tv, true)) {
                mStrokeWidth = context.getResources().getDimensionPixelSize(tv.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_backtrack_size, tv, true)) {
                this.mButtonSize = context.getResources().getDimensionPixelSize(tv.resourceId);
            }
        }

        mCenterX = mButtonSize / 2;
        mCenterY = mButtonSize / 2;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(color);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);

        mPath = new Path();
    }

    public ReturnButton(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mButtonSize, mButtonSize / 2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPath.moveTo(mStrokeWidth, mStrokeWidth / 2);
        mPath.lineTo(mCenterX, mCenterY - mStrokeWidth / 2);
        mPath.lineTo(mButtonSize - mStrokeWidth, mStrokeWidth / 2);
        canvas.drawPath(mPath, mPaint);
    }
}
