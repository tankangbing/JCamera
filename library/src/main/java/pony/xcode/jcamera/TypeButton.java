package pony.xcode.jcamera;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * 描    述：拍照或录制完成后弹出的确认和返回按钮
 */
public class TypeButton extends View {
    public static final int TYPE_CANCEL = 0x001;
    public static final int TYPE_CONFIRM = 0x002;
    private int mButtonType;
    private int mButtonSize;

    private float mCenterX;
    private float mCenterY;
    private float mButtonRadius;

    private Paint mPaint;
    private Path mPath;
    private float mStrokeWidth;

    private float mIndex;
    private RectF mRectF;

    private int mCancelBackground = JCameraConfig.CANCEL_BACKGROUND;
    private int mCancelIconColor = JCameraConfig.CANCEL_ICON_COLOR;
    private int mConfirmBackground = JCameraConfig.CONFIRM_BACKGROUND;
    private int mConfirmIconColor = JCameraConfig.CONFIRM_ICON_COLOR;

    public TypeButton(Context context) {
        super(context);
    }

    public TypeButton(Context context, int type, int size) {
        super(context);
        this.mButtonType = type;
        this.mButtonSize = size;
        this.mStrokeWidth = mButtonSize / 50f;
        Resources.Theme theme = context.getTheme();
        if (theme != null) {
            TypedValue typedValue = new TypedValue();
            if (theme.resolveAttribute(R.attr.jc_type_buttonSize, typedValue, true)) {
                this.mButtonSize = context.getResources().getDimensionPixelSize(typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_type_strokeWidth, typedValue, true)) {
                this.mStrokeWidth = context.getResources().getDimensionPixelSize(typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_cancel_background, typedValue, true)) {
                mCancelBackground = ContextCompat.getColor(context, typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_cancel_icon_color, typedValue, true)) {
                mCancelIconColor = ContextCompat.getColor(context, typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_confirm_background, typedValue, true)) {
                mConfirmBackground = ContextCompat.getColor(context, typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_confirm_icon_color, typedValue, true)) {
                mConfirmIconColor = ContextCompat.getColor(context, typedValue.resourceId);
            }
        }

        mButtonRadius = mButtonSize / 2.0f;
        mCenterX = mButtonSize / 2.0f;
        mCenterY = mButtonSize / 2.0f;

        mPaint = new Paint();
        mPath = new Path();

        mIndex = mButtonSize / 12f;
        mRectF = new RectF(mCenterX, mCenterY - mIndex, mCenterX + mIndex * 2, mCenterY + mIndex);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(mButtonSize, mButtonSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //如果类型为取消，则绘制内部为返回箭头
        if (mButtonType == TYPE_CANCEL) {
            mPaint.setAntiAlias(true);
            mPaint.setColor(mCancelBackground);
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(mCenterX, mCenterY, mButtonRadius, mPaint);

            mPaint.setColor(mCancelIconColor);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(mStrokeWidth);

            mPath.moveTo(mCenterX - mIndex / 7, mCenterY + mIndex);
            mPath.lineTo(mCenterX + mIndex, mCenterY + mIndex);

            mPath.arcTo(mRectF, 90, -180);
            mPath.lineTo(mCenterX - mIndex, mCenterY - mIndex);
            canvas.drawPath(mPath, mPaint);
            mPaint.setStyle(Paint.Style.FILL);
            mPath.reset();
            mPath.moveTo(mCenterX - mIndex, (float) (mCenterY - mIndex * 1.5));
            mPath.lineTo(mCenterX - mIndex, (float) (mCenterY - mIndex / 2.3));
            mPath.lineTo((float) (mCenterX - mIndex * 1.6), mCenterY - mIndex);
            mPath.close();
            canvas.drawPath(mPath, mPaint);

        }
        //如果类型为确认，则绘制绿色勾
        if (mButtonType == TYPE_CONFIRM) {
            mPaint.setAntiAlias(true);
            mPaint.setColor(mConfirmBackground);
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(mCenterX, mCenterY, mButtonRadius, mPaint);
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(mConfirmIconColor);
            mPaint.setStrokeWidth(mStrokeWidth);

            mPath.moveTo(mCenterX - mButtonSize / 6f, mCenterY);
            mPath.lineTo(mCenterX - mButtonSize / 21.2f, mCenterY + mButtonSize / 7.7f);
            mPath.lineTo(mCenterX + mButtonSize / 4.0f, mCenterY - mButtonSize / 8.5f);
            mPath.lineTo(mCenterX - mButtonSize / 21.2f, mCenterY + mButtonSize / 9.4f);
            mPath.close();
            canvas.drawPath(mPath, mPaint);
        }
    }
}
