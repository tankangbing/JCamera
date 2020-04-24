package pony.xcode.jcamera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import pony.xcode.jcamera.listener.CaptureListener;
import pony.xcode.jcamera.listener.TypeListener;


public class CaptureLayout extends FrameLayout {

    private CaptureListener mCaptureListener;    //拍照按钮监听
    private TypeListener mTypeListener;          //拍照或录制后接结果按钮监听

    public void setTypeListener(TypeListener typeListener) {
        this.mTypeListener = typeListener;
    }

    public void setCaptureListener(CaptureListener l) {
        this.mCaptureListener = l;
    }

    private CaptureButton mCaptureBtn;      //拍照按钮
    private TypeButton mConfirmBtn;         //确认按钮
    private TypeButton mCancelBtn;          //取消按钮
    private ReturnButton mReturnBtn;        //返回按钮
    private ImageView mCustomLeftIView;            //左边自定义按钮
    private ImageView mCustomRightIView;            //右边自定义按钮
    private TextView mTipTextView;               //提示文本
    private int mTipTextColor = 0xFFFFFFFF;
    private int mTipTextSize;
    private String mBothText = "轻触拍照，长按摄像";
    private String mCaptureText = "轻触拍照";
    private String mRecorderText = "长按摄像";
    private String mRecordShortText = "录制时间过短";

    private int mLayoutWidth;
    private int mLayoutHeight;
    private int mButtonSize;
    private int mIconLeft = 0;
    private int mIconRight = 0;

    private boolean isFirst = true;

    public CaptureLayout(Context context) {
        this(context, null);
    }

    public CaptureLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("ConstantConditions")
    public CaptureLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(outMetrics);

        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mLayoutWidth = outMetrics.widthPixels;
        } else {
            mLayoutWidth = outMetrics.widthPixels / 2;
        }
        mButtonSize = (int) (mLayoutWidth / 4.5f);
        mLayoutHeight = mButtonSize + (mButtonSize / 5) * 2 + 100;
        final float scale = context.getResources().getDisplayMetrics().scaledDensity;
        mTipTextSize = (int) (13 * scale + 0.5f);
        Resources.Theme theme = context.getTheme();
        if (theme != null) {
            TypedValue typedValue = new TypedValue();
            if (theme.resolveAttribute(R.attr.jc_tips_textColor, typedValue, true)) {
                mTipTextColor = ContextCompat.getColor(context, typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_tips_textSize, typedValue, true)) {
                mTipTextSize = context.getResources().getDimensionPixelSize(typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_tips_both_text, typedValue, true)) {
                mBothText = context.getString(typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_tips_capture_text, typedValue, true)) {
                mCaptureText = context.getString(typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_tips_recorder_text, typedValue, true)) {
                mRecorderText = context.getString(typedValue.resourceId);
            }
            if (theme.resolveAttribute(R.attr.jc_tips_record_short, typedValue, true)) {
                mRecordShortText = context.getString(typedValue.resourceId);
            }
        }
        initView(context);
        initEvent();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(mLayoutWidth, mLayoutHeight);
    }

    public void initEvent() {
        //默认TypeButton为隐藏
        mCustomRightIView.setVisibility(GONE);
        mCancelBtn.setVisibility(GONE);
        mConfirmBtn.setVisibility(GONE);
    }

    public void startTypeBtnAnimator() {
        //拍照录制结果后的动画
        if (this.mIconLeft != 0)
            mCustomLeftIView.setVisibility(GONE);
        else
            mReturnBtn.setVisibility(GONE);
        if (this.mIconRight != 0)
            mCustomRightIView.setVisibility(GONE);
        mCaptureBtn.setVisibility(GONE);
        mCancelBtn.setVisibility(VISIBLE);
        mConfirmBtn.setVisibility(VISIBLE);
        mCancelBtn.setClickable(false);
        mConfirmBtn.setClickable(false);
        ObjectAnimator cancelAnim = ObjectAnimator.ofFloat(mCancelBtn, "translationX", mLayoutWidth / 4f, 0);
        ObjectAnimator confirmAnim = ObjectAnimator.ofFloat(mConfirmBtn, "translationX", -mLayoutWidth / 4f, 0);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(cancelAnim, confirmAnim);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mCancelBtn.setClickable(true);
                mConfirmBtn.setClickable(true);
            }
        });
        set.setDuration(200);
        set.start();
    }


    private void initView(Context context) {
        setWillNotDraw(false);
        //拍照按钮
        mCaptureBtn = new CaptureButton(context, mButtonSize);
        LayoutParams captureBtnParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        captureBtnParams.gravity = Gravity.CENTER;
        mCaptureBtn.setLayoutParams(captureBtnParams);
        mCaptureBtn.setCaptureListener(new CaptureListener() {
            @Override
            public void takePictures() {
                if (mCaptureListener != null) {
                    mCaptureListener.takePictures();
                }
            }

            @Override
            public void recordShort(long time) {
                if (mCaptureListener != null) {
                    mCaptureListener.recordShort(time);
                }
                startAlphaAnimation();
            }

            @Override
            public void recordStart() {
                if (mCaptureListener != null) {
                    mCaptureListener.recordStart();
                }
                startAlphaAnimation();
            }

            @Override
            public void recordEnd(long time) {
                if (mCaptureListener != null) {
                    mCaptureListener.recordEnd(time);
                }
                startAlphaAnimation();
                startTypeBtnAnimator();
            }

            @Override
            public void recordZoom(float zoom) {
                if (mCaptureListener != null) {
                    mCaptureListener.recordZoom(zoom);
                }
            }
        });

        //取消按钮
        mCancelBtn = new TypeButton(context, TypeButton.TYPE_CANCEL, mButtonSize);
        final LayoutParams cancelBtnParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        cancelBtnParams.gravity = Gravity.CENTER_VERTICAL;
        cancelBtnParams.setMargins((mLayoutWidth / 4) - mButtonSize / 2, 0, 0, 0);
        mCancelBtn.setLayoutParams(cancelBtnParams);
        mCancelBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTypeListener != null) {
                    mTypeListener.cancel();
                }
                startAlphaAnimation();
//                resetCaptureLayout();
            }
        });

        //确认按钮
        mConfirmBtn = new TypeButton(context, TypeButton.TYPE_CONFIRM, mButtonSize);
        LayoutParams confirmBtnParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        confirmBtnParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        confirmBtnParams.setMargins(0, 0, (mLayoutWidth / 4) - mButtonSize / 2, 0);
        mConfirmBtn.setLayoutParams(confirmBtnParams);
        mConfirmBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTypeListener != null) {
                    mTypeListener.confirm();
                }
                startAlphaAnimation();
//                resetCaptureLayout();
            }
        });

        //返回按钮
        mReturnBtn = new ReturnButton(context, (int) (mButtonSize / 2.5f));
        LayoutParams mReturnParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mReturnParams.gravity = Gravity.CENTER_VERTICAL;
        mReturnParams.setMargins(mLayoutWidth / 6, 0, 0, 0);
        mReturnBtn.setLayoutParams(mReturnParams);
        //左边自定义按钮
        mCustomLeftIView = new ImageView(context);
        LayoutParams customLeftParams = new LayoutParams((int) (mButtonSize / 2.5f), (int) (mButtonSize / 2.5f));
        customLeftParams.gravity = Gravity.CENTER_VERTICAL;
        customLeftParams.setMargins(mLayoutWidth / 6, 0, 0, 0);
        mCustomLeftIView.setLayoutParams(customLeftParams);

        //右边自定义按钮
        mCustomRightIView = new ImageView(context);
        LayoutParams customRightParams = new LayoutParams((int) (mButtonSize / 2.5f), (int) (mButtonSize / 2.5f));
        customRightParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        customRightParams.setMargins(0, 0, mLayoutWidth / 6, 0);
        mCustomRightIView.setLayoutParams(customRightParams);

        mTipTextView = new TextView(context);
        LayoutParams tipTxtParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tipTxtParams.gravity = Gravity.CENTER_HORIZONTAL;
        tipTxtParams.setMargins(0, 0, 0, 0);
        mTipTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTipTextSize);
        mTipTextView.setTextColor(mTipTextColor);
        mTipTextView.setGravity(Gravity.CENTER);
        mTipTextView.setLayoutParams(tipTxtParams);
        setTip(mBothText);

        this.addView(mCaptureBtn);
        this.addView(mCancelBtn);
        this.addView(mConfirmBtn);
        this.addView(mReturnBtn);
        this.addView(mCustomLeftIView);
        this.addView(mCustomRightIView);
        this.addView(mTipTextView);

    }

    public void setLeftClickListener(View.OnClickListener l) {
        mCustomLeftIView.setOnClickListener(l);
    }

    public void setRightClickListener(View.OnClickListener l) {
        mCustomRightIView.setOnClickListener(l);
    }

    public void setReturnListener(View.OnClickListener l) {
        mReturnBtn.setOnClickListener(l);
    }

    /**************************************************
     * 对外提供的API                      *
     **************************************************/
    public void resetCaptureLayout() {
        mCaptureBtn.resetState();
        mCancelBtn.setVisibility(GONE);
        mConfirmBtn.setVisibility(GONE);
        mCaptureBtn.setVisibility(VISIBLE);
        if (this.mIconLeft != 0)
            mCustomLeftIView.setVisibility(VISIBLE);
        else
            mReturnBtn.setVisibility(VISIBLE);
        if (this.mIconRight != 0)
            mCustomRightIView.setVisibility(VISIBLE);
    }


    public void startAlphaAnimation() {
        if (isFirst) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(mTipTextView, "alpha", 1f, 0f);
            animator.setDuration(500);
            animator.start();
            isFirst = false;
        }
    }

    private ObjectAnimator mTextAnimation;

    public void setRecordShortTipWithAnimation() {
        mTipTextView.setText(mRecordShortText);
        if (mTextAnimation != null) {
            mTextAnimation.end();
            mTextAnimation.cancel();
            mTextAnimation = null;
        }
        mTextAnimation = ObjectAnimator.ofFloat(mTipTextView, "alpha", 0f, 1f, 1f, 0f);
        mTextAnimation.setDuration(2000);
        mTextAnimation.start();
    }

    public void setMinDuration(int minDuration) {
        mCaptureBtn.setMinDuration(minDuration);
    }

    public void setMaxDuration(int maxDuration) {
        mCaptureBtn.setMaxDuration(maxDuration);
    }

    public void setButtonFeatures(int state) {
        mCaptureBtn.setButtonFeatures(state);
        if (state == JCameraView.BUTTON_STATE_ONLY_RECORDER) {
            setTip(mRecorderText);
        } else if (state == JCameraView.BUTTON_STATE_ONLY_CAPTURE) {
            setTip(mCaptureText);
        } else {
            setTip(mBothText);
        }
    }

    public void setTip(String tip) {
        mTipTextView.setText(tip);
    }

    public void setIconSrc(int iconLeft, int iconRight) {
        this.mIconLeft = iconLeft;
        this.mIconRight = iconRight;
        if (this.mIconLeft != 0) {
            mCustomLeftIView.setImageResource(iconLeft);
            mCustomLeftIView.setVisibility(VISIBLE);
            mReturnBtn.setVisibility(GONE);
        } else {
            mCustomLeftIView.setVisibility(GONE);
            mReturnBtn.setVisibility(VISIBLE);
        }
        if (this.mIconRight != 0) {
            mCustomRightIView.setImageResource(iconRight);
            mCustomRightIView.setVisibility(VISIBLE);
        } else {
            mCustomRightIView.setVisibility(GONE);
        }
    }
}
