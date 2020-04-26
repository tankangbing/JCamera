package pony.xcode.jcamera;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.VideoView;


import pony.xcode.jcamera.listener.CaptureListener;
import pony.xcode.jcamera.listener.ErrorListener;
import pony.xcode.jcamera.listener.JCameraListener;
import pony.xcode.jcamera.listener.TypeListener;
import pony.xcode.jcamera.state.CameraMachine;
import pony.xcode.jcamera.util.FileUtil;
import pony.xcode.jcamera.util.LogUtil;
import pony.xcode.jcamera.util.ScreenUtils;
import pony.xcode.jcamera.view.CameraView;

public class JCameraView extends FrameLayout implements CameraInterface.CameraOpenOverCallback, SurfaceHolder
        .Callback, CameraView {
//    private static final String TAG = "JCameraView";

    //Camera状态机
    private CameraMachine mCameraMachine;

    //闪关灯状态
    private static final int TYPE_FLASH_AUTO = 0x021;
    private static final int TYPE_FLASH_ON = 0x022;
    private static final int TYPE_FLASH_OFF = 0x023;
    private int mTypeFlash = TYPE_FLASH_OFF;

    //拍照浏览时候的类型
    public static final int TYPE_PICTURE = 0x001;
    public static final int TYPE_VIDEO = 0x002;
    public static final int TYPE_SHORT = 0x003;
    public static final int TYPE_DEFAULT = 0x004;

    //录制视频比特率
    public static final int MEDIA_QUALITY_HIGH = 20 * 100000;  //5*1024*1024  --20 * 100000
    public static final int MEDIA_QUALITY_MIDDLE = 16 * 100000;
    public static final int MEDIA_QUALITY_LOW = 12 * 100000;
    public static final int MEDIA_QUALITY_POOR = 8 * 100000;
    public static final int MEDIA_QUALITY_FUNNY = 4 * 100000;
    public static final int MEDIA_QUALITY_DESPAIR = 2 * 100000;
    public static final int MEDIA_QUALITY_SORRY = 80000;


    public static final int BUTTON_STATE_ONLY_CAPTURE = 0x101;      //只能拍照
    public static final int BUTTON_STATE_ONLY_RECORDER = 0x102;     //只能录像
    public static final int BUTTON_STATE_BOTH = 0x103;              //两者都可以

    //回调监听
    private JCameraListener mJCameraListener;

    private Context mContext;
    private VideoView mVideoView;
    private ImageView mPhoto;
    private ImageView mSwitchCamera;
    private ImageView mFlashLamp;
    private CaptureLayout mCaptureLayout;
    private FocusView mFocusView;
    private MediaPlayer mMediaPlayer;

    private int mLayoutWidth;
    private float mScreenProp = 0f;

    private Bitmap mCaptureBitmap;   //捕获的图片
    private Bitmap mFirstFrame;      //第一帧图片
    private String mVideoUrl;        //视频URL


    //切换摄像头按钮的参数
    private int mIconCamera;        //图标资源
    private int mMinDuration;  //最小录制时长
    private int mMaxDuration;       //录制时间

    //缩放梯度
    private int mZoomGradient = 0;

    private boolean mFirstTouch = true;
    private float mFirstTouchLength = 0;

    public JCameraView(Context context) {
        this(context, null);
    }

    public JCameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public JCameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        //get AttributeSet
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.JCameraView, defStyleAttr, 0);
        mIconCamera = a.getResourceId(R.styleable.JCameraView_jc_iconCamera, R.drawable.ic_camera);
        mMinDuration = a.getInteger(R.styleable.JCameraView_jc_duration_min, JCameraConfig.DURATION_MIN); //没设置默认1.5s
        mMaxDuration = a.getInteger(R.styleable.JCameraView_jc_duration_max, JCameraConfig.DURATION_MAX);       //没设置默认为10s
        a.recycle();
        initData();
        initView();
    }

    private void initData() {
        mLayoutWidth = ScreenUtils.getScreenWidth(mContext);
        //缩放梯度
        mZoomGradient = (int) (mLayoutWidth / 16f);
        LogUtil.i("zoom = " + mZoomGradient);
        mCameraMachine = new CameraMachine(getContext(), this);
    }

    private void initView() {
        setWillNotDraw(false);
        View view = LayoutInflater.from(mContext).inflate(R.layout.camera_view, this);
        mVideoView = view.findViewById(R.id.video_preview);
        mPhoto = view.findViewById(R.id.image_photo);
        mSwitchCamera = view.findViewById(R.id.image_switch);
        mSwitchCamera.setImageResource(mIconCamera);
        mFlashLamp = view.findViewById(R.id.image_flash);
        Resources.Theme theme = getContext().getTheme();
        if (theme != null) {
            TypedValue typedValue = new TypedValue();
            if (theme.resolveAttribute(R.attr.jc_vector_icon_size, typedValue, true)) {
                final int vectorIconSize = getContext().getResources().getDimensionPixelSize(typedValue.resourceId);
                LinearLayout.LayoutParams switchParams = (LinearLayout.LayoutParams) mSwitchCamera.getLayoutParams();
                switchParams.width = vectorIconSize;
                switchParams.height = vectorIconSize;
                mSwitchCamera.setLayoutParams(switchParams);
                LinearLayout.LayoutParams flashParams = (LinearLayout.LayoutParams) mFlashLamp.getLayoutParams();
                flashParams.width = vectorIconSize;
                flashParams.height = vectorIconSize;
                mFlashLamp.setLayoutParams(flashParams);
            }
        }
        setFlashRes();
        mFlashLamp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTypeFlash++;
                if (mTypeFlash > 0x023)
                    mTypeFlash = TYPE_FLASH_AUTO;
                setFlashRes();
            }
        });
        mCaptureLayout = view.findViewById(R.id.capture_layout);
        mCaptureLayout.setMinDuration(mMinDuration);
        mCaptureLayout.setMaxDuration(mMaxDuration);
        mFocusView = view.findViewById(R.id.focus_view);
        mVideoView.getHolder().addCallback(this);
        //切换摄像头
        mSwitchCamera.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraMachine.onSwitch(mVideoView.getHolder(), mScreenProp);
            }
        });
        //拍照 录像
        mCaptureLayout.setCaptureListener(new CaptureListener() {
            @Override
            public void takePictures() {
                mSwitchCamera.setVisibility(INVISIBLE);
                mFlashLamp.setVisibility(INVISIBLE);
                mCameraMachine.capture();
            }

            @Override
            public void recordStart() {
                mSwitchCamera.setVisibility(INVISIBLE);
                mFlashLamp.setVisibility(INVISIBLE);
                mCameraMachine.record(mVideoView.getHolder().getSurface(), mScreenProp);
            }

            @Override
            public void recordShort(final long time) {
                mCaptureLayout.setRecordShortTipWithAnimation();
                mSwitchCamera.setVisibility(VISIBLE);
                mFlashLamp.setVisibility(VISIBLE);
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mCameraMachine.stopRecord(true, time);
                    }
                }, 1500 - time);
            }

            @Override
            public void recordEnd(long time) {
                mCameraMachine.stopRecord(false, time);
            }

            @Override
            public void recordZoom(float zoom) {
                LogUtil.i("recordZoom");
                mCameraMachine.zoom(zoom, CameraInterface.TYPE_RECORDER);
            }
        });
        //确认 取消
        mCaptureLayout.setTypeListener(new TypeListener() {
            @Override
            public void cancel() {
                mCameraMachine.cancel(mVideoView.getHolder(), mScreenProp);
            }

            @Override
            public void confirm() {
                mCameraMachine.confirm();
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        float widthSize = mVideoView.getMeasuredWidth();
        float heightSize = mVideoView.getMeasuredHeight();
        if (mScreenProp == 0) {
            mScreenProp = heightSize / widthSize;
        }
    }

    @Override
    public void cameraHasOpened() {
        CameraInterface.getInstance().doStartPreview(mVideoView.getHolder(), mScreenProp);
    }

    //生命周期onResume
    public void onResume() {
        LogUtil.i("JCameraView onResume");
        resetState(TYPE_DEFAULT); //重置状态
        CameraInterface.getInstance().registerSensorManager(mContext, mSwitchCamera, mFlashLamp);
        CameraInterface.getInstance().setCameraAngle(mContext);
//        CameraInterface.getInstance().setSwitchView(mSwitchCamera, mFlashLamp);
        mCameraMachine.start(mVideoView.getHolder(), mScreenProp);
    }

    //生命周期onPause
    public void onPause() {
        LogUtil.i("JCameraView onPause");
        resetState(TYPE_PICTURE);
        CameraInterface.getInstance().onPause();
        CameraInterface.getInstance().unregisterSensorManager(mContext);
    }

    //生命周期onDestroy
    public void onDestroy() {
        CameraInterface.getInstance().doDestroyCamera();
    }

    //SurfaceView生命周期
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtil.i("JCameraView SurfaceCreated");
        CameraInterface.getInstance().doOpenCamera(JCameraView.this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtil.i("JCameraView SurfaceDestroyed");
        CameraInterface.getInstance().doDestroyCamera();
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getPointerCount() == 1) {
                    //显示对焦指示器
                    setFocusViewWidthAnimation(event.getX(), event.getY());
                }
                if (event.getPointerCount() == 2) {
                    LogUtil.i("ACTION_DOWN = " + 2);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1) {
                    mFirstTouch = true;
                }
                if (event.getPointerCount() == 2) {
                    //第一个点
                    float point_1_X = event.getX(0);
                    float point_1_Y = event.getY(0);
                    //第二个点
                    float point_2_X = event.getX(1);
                    float point_2_Y = event.getY(1);

                    float result = (float) Math.sqrt(Math.pow(point_1_X - point_2_X, 2) + Math.pow(point_1_Y -
                            point_2_Y, 2));

                    if (mFirstTouch) {
                        mFirstTouchLength = result;
                        mFirstTouch = false;
                    }
                    if ((int) (result - mFirstTouchLength) / mZoomGradient != 0) {
                        mFirstTouch = true;
                        mCameraMachine.zoom(result - mFirstTouchLength, CameraInterface.TYPE_CAPTURE);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                mFirstTouch = true;
                break;
        }
        return true;
    }

    //对焦框指示器动画
    private void setFocusViewWidthAnimation(float x, float y) {
        mCameraMachine.focus(x, y, new CameraInterface.FocusCallback() {
            @Override
            public void focusSuccess() {
                mFocusView.setVisibility(INVISIBLE);
            }
        });
    }

    private void updateVideoViewSize(float videoWidth, float videoHeight) {
        if (videoWidth > videoHeight) {
            LayoutParams videoViewParam;
            int height = (int) ((videoHeight / videoWidth) * getWidth());
            videoViewParam = new LayoutParams(LayoutParams.MATCH_PARENT, height);
            videoViewParam.gravity = Gravity.CENTER;
            mVideoView.setLayoutParams(videoViewParam);
        }
    }

    /**************************************************
     * 对外提供的API                     *
     **************************************************/
    public void setMinDuration(int minDuration) {
        this.mMinDuration = minDuration;
    }

    public void setMaxDuration(int maxDuration) {
        this.mMaxDuration = maxDuration;
    }

    //退出
    public void setReturnListener(View.OnClickListener l) {
        mCaptureLayout.setReturnListener(l);
    }

    public void setSaveVideoPath(String path) {
        CameraInterface.getInstance().setSaveVideoPath(path);
    }

    public void setJCameraListener(JCameraListener l) {
        this.mJCameraListener = l;
    }

    //启动Camera错误回调
    public void setErrorListener(ErrorListener errorListener) {
        CameraInterface.getInstance().setErrorListener(errorListener);
    }

    //设置CaptureButton功能（拍照和录像）
    public void setFeatures(int state) {
        this.mCaptureLayout.setButtonFeatures(state);
    }

    //设置录制质量
    public void setMediaQuality(int quality) {
        CameraInterface.getInstance().setMediaQuality(quality);
    }

    //设置是否获取视频第一帧bitmap
    public void setVideoFirstFrameEnable(boolean enable) {
        CameraInterface.getInstance().setVideoFirstFrameEnable(enable);
    }

    @Override
    public void resetState(int type) {
        switch (type) {
            case TYPE_VIDEO:
                stopVideo();    //停止播放
                //初始化VideoView
                FileUtil.deleteFile(mVideoUrl);
                mVideoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                mCameraMachine.start(mVideoView.getHolder(), mScreenProp);
                break;
            case TYPE_PICTURE:
                mPhoto.setVisibility(INVISIBLE);
                break;
            case TYPE_SHORT:
                break;
            case TYPE_DEFAULT:
                mVideoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                break;
        }
        mSwitchCamera.setVisibility(VISIBLE);
        mFlashLamp.setVisibility(VISIBLE);
        mCaptureLayout.resetCaptureLayout();
    }

    @Override
    public void confirmState(int type) {
        switch (type) {
            case TYPE_VIDEO:
                stopVideo();    //停止播放
                mVideoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                mCameraMachine.start(mVideoView.getHolder(), mScreenProp);
                if (mJCameraListener != null) {
                    mJCameraListener.recordSuccess(mVideoUrl, mFirstFrame);
                }
                break;
            case TYPE_PICTURE:
                mPhoto.setVisibility(INVISIBLE);
                if (mJCameraListener != null) {
                    mJCameraListener.captureSuccess(mCaptureBitmap);
                }
                break;
            case TYPE_SHORT:
            case TYPE_DEFAULT:
                break;
        }
        mCaptureLayout.resetCaptureLayout();
    }

    @Override
    public void showPicture(Bitmap bitmap, boolean isVertical) {
        if (isVertical) {
            mPhoto.setScaleType(ImageView.ScaleType.FIT_XY);
        } else {
            mPhoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        mCaptureBitmap = bitmap;
        mPhoto.setImageBitmap(bitmap);
        mPhoto.setVisibility(VISIBLE);
        mCaptureLayout.startAlphaAnimation();
        mCaptureLayout.startTypeBtnAnimator();
    }

    @Override
    public void playVideo(Bitmap firstFrame, final String url) {
        mVideoUrl = url;
        JCameraView.this.mFirstFrame = firstFrame;
        try {
            if (mMediaPlayer == null) {
                mMediaPlayer = new MediaPlayer();
            } else {
                mMediaPlayer.reset();
            }
            mMediaPlayer.setDataSource(url);
            mMediaPlayer.setSurface(mVideoView.getHolder().getSurface());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            }
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void
                onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    updateVideoViewSize(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
                }
            });
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mMediaPlayer.start();
                }
            });
            mMediaPlayer.setLooping(true);
            mMediaPlayer.prepareAsync();
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
        } catch (Exception e) {
            LogUtil.e("play video failed");
        }
    }

    @Override
    public void stopVideo() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

//    @Override
//    public void setTip(String tip) {
//        mCaptureLayout.setTip(tip);
//    }
//
//    @Override
//    public void startPreviewCallback() {
//        handlerFocus(mFocusView.getWidth() / 2, mFocusView.getHeight() / 2);
//    }

    @Override
    public boolean handlerFocus(float x, float y) {
        if (y > mCaptureLayout.getTop()) {
            return false;
        }
        mFocusView.setVisibility(VISIBLE);
        if (x < mFocusView.getWidth() / 2f) {
            x = mFocusView.getWidth() / 2f;
        }
        if (x > mLayoutWidth - mFocusView.getWidth() / 2f) {
            x = mLayoutWidth - mFocusView.getWidth() / 2f;
        }
        if (y < mFocusView.getWidth() / 2f) {
            y = mFocusView.getWidth() / 2f;
        }
        if (y > mCaptureLayout.getTop() - mFocusView.getWidth() / 2f) {
            y = mCaptureLayout.getTop() - mFocusView.getWidth() / 2f;
        }
        mFocusView.setX(x - mFocusView.getWidth() / 2f);
        mFocusView.setY(y - mFocusView.getHeight() / 2f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mFocusView, "scaleX", 1, 0.6f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mFocusView, "scaleY", 1, 0.6f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(mFocusView, "alpha", 1f, 0.4f, 1f, 0.4f, 1f, 0.4f, 1f);
        AnimatorSet animSet = new AnimatorSet();
        animSet.play(scaleX).with(scaleY).before(alpha);
        animSet.setDuration(400);
        animSet.start();
        return true;
    }


    private void setFlashRes() {
        switch (mTypeFlash) {
            case TYPE_FLASH_AUTO:
                mFlashLamp.setImageResource(R.drawable.ic_flash_auto);
                mCameraMachine.flash(Camera.Parameters.FLASH_MODE_AUTO);
                break;
            case TYPE_FLASH_ON:
                mFlashLamp.setImageResource(R.drawable.ic_flash_on);
                mCameraMachine.flash(Camera.Parameters.FLASH_MODE_ON);
                break;
            case TYPE_FLASH_OFF:
                mFlashLamp.setImageResource(R.drawable.ic_flash_off);
                mCameraMachine.flash(Camera.Parameters.FLASH_MODE_OFF);
                break;
        }
    }
}
