package pony.xcode.jcamera;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pony.xcode.jcamera.listener.ErrorListener;
import pony.xcode.jcamera.util.AngleUtil;
import pony.xcode.jcamera.util.CameraParamUtil;
import pony.xcode.jcamera.util.CheckPermission;
import pony.xcode.jcamera.util.FileUtil;
import pony.xcode.jcamera.util.LogUtil;
import pony.xcode.jcamera.util.ScreenUtils;


public class CameraInterface implements Camera.PreviewCallback {

    private volatile static CameraInterface mCameraInterface;

    public static void destroyCameraInterface() {
        if (mCameraInterface != null) {
            mCameraInterface = null;
        }
    }

    private Camera mCamera;
    private Camera.Parameters mParams;
    private boolean mPreviewing = false;

    private int SELECTED_CAMERA;
    private int CAMERA_POST_POSITION = -1;
    private int CAMERA_FRONT_POSITION = -1;

    private float mScreenProp = -1.0f;

    private boolean mRecording = false;
    private MediaRecorder mMediaRecorder;
    private String mVideoFileName;
    private String mSaveVideoPath;
    private String mVideoFileAbsPath;    //视频路径
    private Bitmap mVideoFirstFrame = null; //视频第一帧

    private ErrorListener mErrorListener;

    private int mPreviewWidth;
    private int mPreviewHeight;

    private int mAngle = 0;
    private int mCameraAngle = 90;//摄像头角度   默认为90度
    private int mRotation = 0;
    private byte[] mFirstFrameData;

    static final int TYPE_RECORDER = 0x090;
    static final int TYPE_CAPTURE = 0x091;
    private int mNowScaleRate = 0;
    private int mRecordScaleRate = 0;

    //视频质量
    private int mMediaQuality = JCameraView.MEDIA_QUALITY_MIDDLE;
    private boolean mSaveVideoFirstFrame = false; //是否保存视频的第一帧
    private SensorManager mSensorManager = null;
    private boolean mSafeToTakePicture = false;

    //获取CameraInterface单例
    public static synchronized CameraInterface getInstance() {
        if (mCameraInterface == null)
            synchronized (CameraInterface.class) {
                if (mCameraInterface == null)
                    mCameraInterface = new CameraInterface();
            }
        return mCameraInterface;
    }

    void setCameraAngle(Context context) {
        mCameraAngle = CameraParamUtil.getInstance().getCameraDisplayOrientation(context, SELECTED_CAMERA);
    }

//    void setSwitchView(ImageView switchView, ImageView flashLamp) {
//        this.mSwitchView = switchView;
//        this.mFlashLamp = flashLamp;
//        if (mSwitchView != null) {
//            cameraAngle = CameraParamUtil.getInstance().getCameraDisplayOrientation(mSwitchView.getContext(),
//                    SELECTED_CAMERA);
//        }
//    }

    private final class MySensorEventListener implements SensorEventListener {
        private ImageView mSwitchView;
        private ImageView mFlashLamp;

        MySensorEventListener(ImageView switchView, ImageView flashLampView) {
            this.mSwitchView = switchView;
            this.mFlashLamp = flashLampView;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (Sensor.TYPE_ACCELEROMETER != event.sensor.getType()) {
                return;
            }
            float[] values = event.values;
            mAngle = AngleUtil.getSensorAngle(values[0], values[1]);
            rotationAnimation(mSwitchView, mFlashLamp);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    //切换摄像头icon跟随手机角度进行旋转
    private void rotationAnimation(ImageView switchView, ImageView flashLampView) {
        if (switchView == null) {
            return;
        }
        if (mRotation != mAngle) {
            int start_rotation = 0;
            int end_rotation = 0;
            switch (mRotation) {
                case 0:
                    start_rotation = 0;
                    switch (mAngle) {
                        case 90:
                            end_rotation = -90;
                            break;
                        case 270:
                            end_rotation = 90;
                            break;
                    }
                    break;
                case 90:
                    start_rotation = -90;
                    switch (mAngle) {
                        case 0:
                            end_rotation = 0;
                            break;
                        case 180:
                            end_rotation = -180;
                            break;
                    }
                    break;
                case 180:
                    start_rotation = 180;
                    switch (mAngle) {
                        case 90:
                            end_rotation = 270;
                            break;
                        case 270:
                            end_rotation = 90;
                            break;
                    }
                    break;
                case 270:
                    start_rotation = 90;
                    switch (mAngle) {
                        case 0:
                            end_rotation = 0;
                            break;
                        case 180:
                            end_rotation = 180;
                            break;
                    }
                    break;
            }
            ObjectAnimator animC = ObjectAnimator.ofFloat(switchView, "rotation", start_rotation, end_rotation);
            ObjectAnimator animF = ObjectAnimator.ofFloat(flashLampView, "rotation", start_rotation, end_rotation);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(animC, animF);
            set.setDuration(500);
            set.start();
            mRotation = mAngle;
        }
    }

    void setSaveVideoPath(@Nullable String saveVideoPath) {
        if (saveVideoPath != null) {
            this.mSaveVideoPath = saveVideoPath;
            File file = new File(saveVideoPath);
            if (!file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.mkdirs();
            }
        }
    }


    public void setZoom(float zoom, int type) {
        if (mCamera == null) {
            return;
        }
        try {
            if (mParams == null) {
                mParams = mCamera.getParameters();
            }
            if (!mParams.isZoomSupported() || !mParams.isSmoothZoomSupported()) {
                return;
            }
            switch (type) {
                case TYPE_RECORDER:
                    //如果不是录制视频中，上滑不会缩放
                    if (!mRecording) {
                        return;
                    }
                    if (zoom >= 0) {
                        //每移动50个像素缩放一个级别
                        int scaleRate = (int) (zoom / 40);
                        if (scaleRate <= mParams.getMaxZoom() && scaleRate >= mNowScaleRate && mRecordScaleRate != scaleRate) {
                            mParams.setZoom(scaleRate);
                            mCamera.setParameters(mParams);
                            mRecordScaleRate = scaleRate;
                        }
                    }
                    break;
                case TYPE_CAPTURE:
                    if (mRecording) {
                        return;
                    }
                    //每移动50个像素缩放一个级别
                    int scaleRate = (int) (zoom / 50);
                    if (scaleRate < mParams.getMaxZoom()) {
                        mNowScaleRate += scaleRate;
                        if (mNowScaleRate < 0) {
                            mNowScaleRate = 0;
                        } else if (mNowScaleRate > mParams.getMaxZoom()) {
                            mNowScaleRate = mParams.getMaxZoom();
                        }
                        mParams.setZoom(mNowScaleRate);
                        mCamera.setParameters(mParams);
                    }
                    LogUtil.i("setZoom = " + mNowScaleRate);
                    break;
            }
        } catch (Exception e) {
            LogUtil.e("setZoom failed " + e.getMessage());
        }

    }

    void setMediaQuality(int quality) {
        this.mMediaQuality = quality;
    }

    void setVideoFirstFrameEnable(boolean enable) {
        this.mSaveVideoFirstFrame = enable;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mFirstFrameData = data;
    }

    public void setFlashMode(String flashMode) {
        if (mCamera == null)
            return;
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(flashMode);
        mCamera.setParameters(params);
    }


    public interface CameraOpenOverCallback {
        void cameraHasOpened();
    }

    private CameraInterface() {
        findAvailableCameras();
        SELECTED_CAMERA = CAMERA_POST_POSITION;
        mSaveVideoPath = "";
    }


    /**
     * open Camera
     */
    void doOpenCamera(CameraOpenOverCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (!CheckPermission.isCameraUsable(SELECTED_CAMERA) && this.mErrorListener != null) {
                this.mErrorListener.onError();
                return;
            }
        }
        if (mCamera == null) {
            openCamera(SELECTED_CAMERA);
        }
        callback.cameraHasOpened();
    }

    private synchronized void openCamera(int id) {
        try {
            this.mCamera = Camera.open(id);
        } catch (Exception e) {
            if (this.mErrorListener != null) {
                this.mErrorListener.onError();
            }
        }

        if (Build.VERSION.SDK_INT > 17 && this.mCamera != null) {
            try {
                this.mCamera.enableShutterSound(false);
            } catch (Exception e) {
                LogUtil.e("enable shutter sound failed " + e.getMessage());
            }
        }
    }

    public synchronized void switchCamera(SurfaceHolder holder, float screenProp) {
        if (SELECTED_CAMERA == CAMERA_POST_POSITION) {
            SELECTED_CAMERA = CAMERA_FRONT_POSITION;
        } else {
            SELECTED_CAMERA = CAMERA_POST_POSITION;
        }
        doDestroyCamera();
        LogUtil.i("open start");
        openCamera(SELECTED_CAMERA);
//        mCamera = Camera.open();
        if (Build.VERSION.SDK_INT > 17 && this.mCamera != null) {
            try {
                this.mCamera.enableShutterSound(false);
            } catch (Exception e) {
                LogUtil.e("enable shutter sound failed " + e.getMessage());
            }
        }
        LogUtil.i("open end");
        doStartPreview(holder, screenProp);
    }

    /**
     * doStartPreview
     */
    public void doStartPreview(SurfaceHolder holder, float screenProp) {
        if (mPreviewing) {
            LogUtil.i("doStartPreview isPreviewing");
        }
        if (this.mScreenProp < 0) {
            this.mScreenProp = screenProp;
        }
        if (holder == null) {
            return;
        }
        if (mCamera != null) {
            try {
                mParams = mCamera.getParameters();
                Camera.Size previewSize = CameraParamUtil.getInstance().getPreviewSize(mParams
                        .getSupportedPreviewSizes(), 1000, screenProp);
                Camera.Size pictureSize = CameraParamUtil.getInstance().getPictureSize(mParams
                        .getSupportedPictureSizes(), 1200, screenProp);
                mParams.setPreviewSize(previewSize.width, previewSize.height);
                mPreviewWidth = previewSize.width;
                mPreviewHeight = previewSize.height;
                mParams.setPictureSize(pictureSize.width, pictureSize.height);
                if (CameraParamUtil.getInstance().isSupportedFocusMode(
                        mParams.getSupportedFocusModes(),
                        Camera.Parameters.FOCUS_MODE_AUTO)) {
                    mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
                if (CameraParamUtil.getInstance().isSupportedPictureFormats(mParams.getSupportedPictureFormats(),
                        ImageFormat.JPEG)) {
                    mParams.setPictureFormat(ImageFormat.JPEG);
                    mParams.setJpegQuality(100);
                }
                mCamera.setParameters(mParams);
                mParams = mCamera.getParameters();
                mCamera.setPreviewDisplay(holder);  //SurfaceView
                mCamera.setDisplayOrientation(mCameraAngle);//浏览角度
                mCamera.setPreviewCallback(this); //每一帧回调
                mCamera.startPreview();//启动浏览
                mPreviewing = true;
                mSafeToTakePicture = true;
                LogUtil.i("=== Start Preview ===");
            } catch (Exception e) {
                LogUtil.e("start preview failed" + e.getMessage());
            }
        }
    }

    /**
     * 停止预览
     */
    public void doStopPreview() {
        if (null != mCamera) {
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                //这句要在stopPreview后执行，不然会卡顿或者花屏
                mCamera.setPreviewDisplay(null);
                mPreviewing = false;
                LogUtil.i("=== Stop Preview ===");
            } catch (Exception e) {
                LogUtil.e("stop preview failed " + e.getMessage());
            }
        }
    }

    /**
     * 销毁Camera
     */
    void doDestroyCamera() {
        mErrorListener = null;
        if (null != mCamera) {
            try {
                mCamera.setPreviewCallback(null);
//                mSwitchView = null;
//                mFlashLamp = null;
                mCamera.stopPreview();
                //这句要在stopPreview后执行，不然会卡顿或者花屏
                mCamera.setPreviewDisplay(null);
                mPreviewing = false;
                mCamera.release();
                mCamera = null;
//                destroyCameraInterface();
                LogUtil.i("=== Destroy Camera ===");
            } catch (Exception e) {
                LogUtil.e("destroy camera failed" + e.getMessage());
            }
        } else {
            LogUtil.i("=== Camera  Null===");
        }
    }

    /**
     * 拍照
     */
    private int mNowAngle;

    public void takePicture(final TakePictureCallback callback) {
        if (mCamera == null) {
            return;
        }
        switch (mCameraAngle) {
            case 90:
                mNowAngle = Math.abs(mAngle + mCameraAngle) % 360;
                break;
            case 270:
                mNowAngle = Math.abs(mCameraAngle - mAngle);
                break;
        }
//
        LogUtil.i(mAngle + " = " + mCameraAngle + " = " + mNowAngle);
        if (mSafeToTakePicture) {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    Matrix matrix = new Matrix();
                    if (SELECTED_CAMERA == CAMERA_POST_POSITION) {
                        matrix.setRotate(mNowAngle);
                    } else if (SELECTED_CAMERA == CAMERA_FRONT_POSITION) {
                        matrix.setRotate(360 - mNowAngle);
                        matrix.postScale(-1, 1);
                    }
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (callback != null) {
                        if (mNowAngle == 90 || mNowAngle == 270) {
                            callback.captureResult(bitmap, true);
                        } else {
                            callback.captureResult(bitmap, false);
                        }
                    }
                }
            });
            mSafeToTakePicture = false;
        }
    }

    //启动录像
    public void startRecord(Surface surface, float screenProp) {
        final int nowAngle = (mAngle + 90) % 360;
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            if (mSaveVideoFirstFrame) {
                try {
                    //获取第一帧图片
                    Camera.Parameters parameters = mCamera.getParameters();
                    int width = parameters.getPreviewSize().width;
                    int height = parameters.getPreviewSize().height;
                    YuvImage yuv = new YuvImage(mFirstFrameData, parameters.getPreviewFormat(), width, height, null);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);
                    byte[] bytes = out.toByteArray();
                    mVideoFirstFrame = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    Matrix matrix = new Matrix();
                    if (SELECTED_CAMERA == CAMERA_POST_POSITION) {
                        matrix.setRotate(nowAngle);
                    } else if (SELECTED_CAMERA == CAMERA_FRONT_POSITION) {
                        matrix.setRotate(270);
                    }
                    mVideoFirstFrame = Bitmap.createBitmap(mVideoFirstFrame, 0, 0, mVideoFirstFrame.getWidth(), mVideoFirstFrame.getHeight(), matrix, true);
                } catch (Exception e) {
                    LogUtil.e("failed to get video first frame " + e.getMessage());
                }
            }
        }
        if (mRecording) {
            return;
        }
        if (mCamera == null) {
            openCamera(SELECTED_CAMERA);
        }
        if (mCamera == null) {
            return;
        }
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        try {
            if (mParams == null) {
                mParams = mCamera.getParameters();
            }
            List<String> focusModes = mParams.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            mCamera.setParameters(mParams);
            mCamera.unlock();
        } catch (Exception e) {
            LogUtil.e("camera getParameters failed " + e.getMessage());
        }
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.reset();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        if (mParams != null) {
            Camera.Size videoSize;
            if (mParams.getSupportedVideoSizes() == null) {
                videoSize = CameraParamUtil.getInstance().getPreviewSize(mParams.getSupportedPreviewSizes(), 600, screenProp);
            } else {
                videoSize = CameraParamUtil.getInstance().getPreviewSize(mParams.getSupportedVideoSizes(), 600, screenProp);
            }
            if (videoSize.width == videoSize.height) {
                mMediaRecorder.setVideoSize(mPreviewWidth, mPreviewHeight);
            } else {
                mMediaRecorder.setVideoSize(videoSize.width, videoSize.height);
            }
        }
        if (SELECTED_CAMERA == CAMERA_FRONT_POSITION) {
            //手机预览倒立的处理
            if (mCameraAngle == 270) {
                //横屏
                if (nowAngle == 0) {
                    mMediaRecorder.setOrientationHint(180);
                } else if (nowAngle == 270) {
                    mMediaRecorder.setOrientationHint(270);
                } else {
                    mMediaRecorder.setOrientationHint(90);
                }
            } else {
                if (nowAngle == 90) {
                    mMediaRecorder.setOrientationHint(270);
                } else if (nowAngle == 270) {
                    mMediaRecorder.setOrientationHint(90);
                } else {
                    mMediaRecorder.setOrientationHint(nowAngle);
                }
            }
        } else {
            mMediaRecorder.setOrientationHint(nowAngle);
        }
        try {
            mMediaRecorder.setVideoEncodingBitRate(mMediaQuality);
        } catch (Exception e) {
            mMediaRecorder.setVideoEncodingBitRate(JCameraView.MEDIA_QUALITY_FUNNY);
        }
        mMediaRecorder.setPreviewDisplay(surface);
        mVideoFileName = "video_" + System.currentTimeMillis() + ".mp4";
        if (mSaveVideoPath == null || mSaveVideoPath.equals("")) {
            mSaveVideoPath = Environment.getExternalStorageDirectory().getPath();
        }
        mVideoFileAbsPath = mSaveVideoPath + File.separator + mVideoFileName;
        mMediaRecorder.setOutputFile(mVideoFileAbsPath);
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            mRecording = true;
        } catch (Exception e) {
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            mRecording = false;
        }
    }

    //停止录像
    public void stopRecord(boolean isShort, StopRecordCallback callback) {
        if (!mRecording) {
            return;
        }
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.setPreviewDisplay(null);
            try {
                mMediaRecorder.stop();
            } catch (Exception e) {
                LogUtil.e("MediaRecorder stop failed");
                mMediaRecorder = null;
            } finally {
                if (mMediaRecorder != null) {
                    mMediaRecorder.release();
                    LogUtil.i("MediaRecorder release.");
                }
                mMediaRecorder = null;
                mRecording = false;
            }
            if (isShort) {
                if (FileUtil.deleteFile(mVideoFileAbsPath)) {
                    callback.recordResult(null, null);
                }
                return;
            }
            doStopPreview();
            String videoPath = mSaveVideoPath + File.separator + mVideoFileName;
            callback.recordResult(videoPath, mVideoFirstFrame);
        }
    }

    private void findAvailableCameras() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int cameraNum = Camera.getNumberOfCameras();
        for (int i = 0; i < cameraNum; i++) {
            Camera.getCameraInfo(i, info);
            switch (info.facing) {
                case Camera.CameraInfo.CAMERA_FACING_FRONT:
                    CAMERA_FRONT_POSITION = info.facing;
                    break;
                case Camera.CameraInfo.CAMERA_FACING_BACK:
                    CAMERA_POST_POSITION = info.facing;
                    break;
            }
        }
    }

    private int handlerTime = 0;

    public void handleFocus(final Context context, final float x, final float y, final FocusCallback callback) {
        if (mCamera == null) {
            return;
        }
        try {
            final Camera.Parameters params = mCamera.getParameters();
            Rect focusRect = calculateTapArea(x, y, context);
            mCamera.cancelAutoFocus();
            if (params.getMaxNumFocusAreas() > 0) {
                List<Camera.Area> focusAreas = new ArrayList<>();
                focusAreas.add(new Camera.Area(focusRect, 800));
                params.setFocusAreas(focusAreas);
            } else {
                LogUtil.i("focus areas not supported");
                callback.focusSuccess();
                return;
            }
            final String currentFocusMode = params.getFocusMode();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(params);
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (success || handlerTime > 10) {
                        Camera.Parameters params = camera.getParameters();
                        params.setFocusMode(currentFocusMode);
                        camera.setParameters(params);
                        handlerTime = 0;
                        callback.focusSuccess();
                    } else {
                        handlerTime++;
                        handleFocus(context, x, y, callback);
                    }
                }
            });
        } catch (Exception e) {
            LogUtil.e("autoFocus failed");
        }
    }

    private Rect calculateTapArea(float x, float y, Context context) {
        float focusAreaSize = 300;
        int areaSize = Float.valueOf(focusAreaSize * 1f).intValue();
        int centerX = (int) (x / ScreenUtils.getScreenWidth(context) * 2000 - 1000);
        int centerY = (int) (y / ScreenUtils.getScreenHeight(context) * 2000 - 1000);
        int left = clamp(centerX - areaSize / 2);
        int top = clamp(centerY - areaSize / 2);
        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x) {
        final int min = -1000;
        final int max = 1000;
        if (x > max) {
            return max;
        }
        return Math.max(x, min);
    }

    void setErrorListener(ErrorListener errorListener) {
        this.mErrorListener = errorListener;
    }

    public interface StopRecordCallback {
        void recordResult(@Nullable String url, @Nullable Bitmap firstFrame);
    }

    public interface TakePictureCallback {
        void captureResult(Bitmap bitmap, boolean isVertical);
    }

    public interface FocusCallback {
        void focusSuccess();

    }

    private MySensorEventListener sensorEventListener;

    @SuppressWarnings("ConstantConditions")
    void registerSensorManager(Context context, ImageView switchView, ImageView flashLampView) {
        if (mSensorManager == null) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        sensorEventListener = new MySensorEventListener(switchView, flashLampView);
        mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager
                .SENSOR_DELAY_NORMAL);
    }

    @SuppressWarnings("ConstantConditions")
    void unregisterSensorManager(Context context) {
        if (mSensorManager == null) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        if (sensorEventListener != null)
            mSensorManager.unregisterListener(sensorEventListener);
    }

    void onPause() {
        this.mPreviewing = false;
    }
}
