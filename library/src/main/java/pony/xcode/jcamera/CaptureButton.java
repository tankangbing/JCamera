package pony.xcode.jcamera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.CountDownTimer;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import pony.xcode.jcamera.listener.CaptureListener;
import pony.xcode.jcamera.util.LogUtil;


public class CaptureButton extends View {

    private int mState;              //当前按钮状态
    private int mButtonState;       //按钮可执行的功能状态（拍照,录制,两者）

    public static final int STATE_IDLE = 0x001;        //空闲状态
    public static final int STATE_PRESS = 0x002;       //按下状态
    public static final int STATE_LONG_PRESS = 0x003;  //长按状态
    public static final int STATE_RECORDING = 0x004; //录制状态
    public static final int STATE_BAN = 0x005;         //禁止状态

    private int mProgressColor = JCameraConfig.PROGRESS_COLOR; //进度条颜色
    private int mOutsideColor = JCameraConfig.OUTSIDE_COLOR; //外圆背景色
    private int mInsideColor = JCameraConfig.INSIDE_COLOR; //内圆背景色

    private float mEventY;  //Touch_Event_Down时候记录的Y值


    private Paint mPaint;

    private float mStrokeWidth;          //进度条宽度
    private int mOutsideAddSize;       //长按外圆半径变大的Size
    private int mInsideReduceSize;     //长安内圆缩小的Size

    //中心坐标
    private float mCenterX;
    private float mCenterY;

    private float mButtonRadius;            //按钮半径
    private float mButtonOutsideRadius;    //外圆半径
    private float mButtonInsideRadius;     //内圆半径
    private int mButtonSize;                //按钮大小

    private float mProgress;         //录制视频的进度
    private int mMaxDuration;           //录制视频最大时间长度
    private int mMinDuration;       //最短录制时间限制
    private int mRecordedTime;      //记录当前录制的时间

    private RectF mRectF;

    private LongPressRunnable mLongPressRunnable;    //长按后处理的逻辑Runnable
    private CaptureListener mCaptureListener;        //按钮回调接口
    private RecordCountDownTimer mCountDownTimer;             //计时器

    public CaptureButton(Context context) {
        super(context);
    }

    public CaptureButton(Context context, int size) {
        super(context);
        Resources.Theme theme = context.getTheme();
        if (theme != null) {
            TypedValue typedValue = new TypedValue();
            if (theme.resolveAttribute(R.attr.jc_progress_color, typedValue, true)) {
                mProgressColor = ContextCompat.getColor(context, typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_outside_color, typedValue, true)) {
                mOutsideColor = ContextCompat.getColor(context, typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_inside_color, typedValue, true)) {
                mInsideColor = ContextCompat.getColor(context, typedValue.resourceId);
            }
        }
        this.mButtonSize = size;
        mButtonRadius = size / 2.0f;

        mButtonOutsideRadius = mButtonRadius;
        mButtonInsideRadius = mButtonRadius * 0.75f;

        mStrokeWidth = size / 15f;
        mOutsideAddSize = size / 5;
        mInsideReduceSize = size / 8;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mProgress = 0;
        mLongPressRunnable = new LongPressRunnable();

        mState = STATE_IDLE;                //初始化为空闲状态
        mButtonState = JCameraView.BUTTON_STATE_BOTH;  //初始化按钮为可录制可拍照
        mMaxDuration = JCameraConfig.DURATION_MAX;              //默认最长录制时间为10s
        mMinDuration = JCameraConfig.DURATION_MIN;              //默认最短录制时间为1.5s

        mCenterX = (mButtonSize + mOutsideAddSize * 2) / 2f;
        mCenterY = (mButtonSize + mOutsideAddSize * 2) / 2f;

        mRectF = new RectF(
                mCenterX - (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2),
                mCenterY - (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2),
                mCenterX + (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2),
                mCenterY + (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2));

        mCountDownTimer = new RecordCountDownTimer(mMaxDuration, mMaxDuration / 360);    //录制定时器
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(mButtonSize + mOutsideAddSize * 2, mButtonSize + mOutsideAddSize * 2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setStyle(Paint.Style.FILL);

        mPaint.setColor(mOutsideColor); //外圆（半透明灰色）
        canvas.drawCircle(mCenterX, mCenterY, mButtonOutsideRadius, mPaint);

        mPaint.setColor(mInsideColor);  //内圆（白色）
        canvas.drawCircle(mCenterX, mCenterY, mButtonInsideRadius, mPaint);

        //如果状态为录制状态，则绘制录制进度条
        if (mState == STATE_RECORDING) {
            mPaint.setColor(mProgressColor);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(mStrokeWidth);
            canvas.drawArc(mRectF, -90, mProgress, false, mPaint);
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(mLongPressRunnable);
                LogUtil.i("state = " + mState);
                if (event.getPointerCount() > 1 || !isIdle()) {
                    resetState();
                    break;
                }
                mEventY = event.getY();     //记录Y值
                mState = STATE_PRESS;        //修改当前状态为点击按下

                //判断按钮状态是否为可录制状态
                if ((mButtonState == JCameraView.BUTTON_STATE_ONLY_RECORDER || mButtonState == JCameraView.BUTTON_STATE_BOTH)) {
                    postDelayed(mLongPressRunnable, 500);    //同时延长500启动长按后处理的逻辑Runnable
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mCaptureListener != null && mState == STATE_RECORDING
                        && (mButtonState == JCameraView.BUTTON_STATE_ONLY_RECORDER
                        || mButtonState == JCameraView.BUTTON_STATE_BOTH)) {
                    //记录当前Y值与按下时候Y值的差值，调用缩放回调接口
                    mCaptureListener.recordZoom(mEventY - event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
                //根据当前按钮的状态进行相应的处理
                handlerUnPressByState();
                break;
            default:
                invalidate();
                resetRecordAnim();
                break;
        }
        return true;
    }

    //当手指松开按钮时候处理的逻辑
    private void handlerUnPressByState() {
        removeCallbacks(mLongPressRunnable); //移除长按逻辑的Runnable
        //根据当前状态处理
        switch (mState) {
            //当前是点击按下
            case STATE_PRESS:
                if (mCaptureListener != null && (mButtonState == JCameraView.BUTTON_STATE_ONLY_CAPTURE || mButtonState ==
                        JCameraView.BUTTON_STATE_BOTH)) {
                    startCaptureAnimation();
                } else {
                    mState = STATE_IDLE;
                }
                break;
            //当前是长按状态
            case STATE_RECORDING:
                mCountDownTimer.cancel(); //停止计时器
                recordEnd();    //录制结束
                break;
        }
    }

    //录制结束
    private void recordEnd() {
        if (mCaptureListener != null) {
            if (mRecordedTime < mMinDuration)
                mCaptureListener.recordShort(mRecordedTime);//回调录制时间过短
            else
                mCaptureListener.recordEnd(mRecordedTime);  //回调录制结束
        }
        resetRecordAnim();  //重制按钮状态
    }

    //重制状态
    private void resetRecordAnim() {
        mState = STATE_BAN;
        mProgress = 0;       //重制进度
        invalidate();
        //还原按钮初始状态动画
        startRecordAnimation(mButtonOutsideRadius, mButtonRadius, mButtonInsideRadius, mButtonRadius * 0.75f);
    }

    private ValueAnimator mInsideAnim;

    //内圆动画
    private void startCaptureAnimation() {
        if (mInsideAnim != null) {
            mInsideAnim.cancel();
            mInsideAnim = null;
        }
        mInsideAnim = ValueAnimator.ofFloat(mButtonInsideRadius, mButtonInsideRadius * 0.75f, mButtonInsideRadius);
        mInsideAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mButtonInsideRadius = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        mInsideAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                //回调拍照接口
                mCaptureListener.takePictures();
                mState = STATE_BAN;
            }
        });
        mInsideAnim.setDuration(100);
        mInsideAnim.start();
    }

    private AnimatorSet mAnimatorSet;

    //内外圆动画
    private void startRecordAnimation(float outside_start, float outside_end, float inside_start, float inside_end) {
        if (mAnimatorSet != null) {
            mAnimatorSet.end();
            mAnimatorSet.cancel();
            mAnimatorSet = null;
        }
        ValueAnimator outsideAnim = ValueAnimator.ofFloat(outside_start, outside_end);
        ValueAnimator insideAnim = ValueAnimator.ofFloat(inside_start, inside_end);
        //外圆动画监听
        outsideAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mButtonOutsideRadius = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        //内圆动画监听
        insideAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mButtonInsideRadius = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        mAnimatorSet = new AnimatorSet();
        //当动画结束后启动录像Runnable并且回调录像开始接口
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                //设置为录制状态
                if (mState == STATE_LONG_PRESS) {
                    if (mCaptureListener != null)
                        mCaptureListener.recordStart();
                    mState = STATE_RECORDING;
                    mCountDownTimer.start();
                }
            }
        });
        mAnimatorSet.playTogether(outsideAnim, insideAnim);
        mAnimatorSet.setDuration(100);
        mAnimatorSet.start();
    }


    //更新进度条
    private void updateProgress(long millisUntilFinished) {
        mRecordedTime = (int) (mMaxDuration - millisUntilFinished);
        mProgress = 360f - millisUntilFinished / (float) mMaxDuration * 360f;
        invalidate();
    }

    //录制视频计时器
    private class RecordCountDownTimer extends CountDownTimer {
        RecordCountDownTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            updateProgress(millisUntilFinished);
        }

        @Override
        public void onFinish() {
            updateProgress(0);
            recordEnd();
        }
    }

    //长按线程
    private class LongPressRunnable implements Runnable {
        @Override
        public void run() {
            mState = STATE_LONG_PRESS;   //如果按下后经过500毫秒则会修改当前状态为长按状态
            //启动按钮动画，外圆变大，内圆缩小
            startRecordAnimation(mButtonOutsideRadius, mButtonOutsideRadius + mOutsideAddSize,
                    mButtonInsideRadius, mButtonInsideRadius - mInsideReduceSize);
        }
    }

    /**************************************************
     * 对外提供的API                     *
     **************************************************/

    //设置最长录制时间
    public void setMaxDuration(int duration) {
        this.mMaxDuration = duration;
        mCountDownTimer = new RecordCountDownTimer(duration, duration / 360);    //录制定时器
    }

    //设置最短录制时间
    public void setMinDuration(int duration) {
        this.mMinDuration = duration;
    }

    //设置回调接口
    public void setCaptureListener(CaptureListener captureListener) {
        this.mCaptureListener = captureListener;
    }

    //设置按钮功能（拍照和录像）
    public void setButtonFeatures(int state) {
        this.mButtonState = state;
    }

    //是否空闲状态
    public boolean isIdle() {
        return mState == STATE_IDLE;
    }

    //设置状态
    public void resetState() {
        mState = STATE_IDLE;
        mCountDownTimer.cancel();
    }
}
