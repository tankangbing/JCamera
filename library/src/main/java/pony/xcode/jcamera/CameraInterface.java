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
import java.io.IOException;
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
    private boolean isPreviewing = false;

    private int SELECTED_CAMERA;
    private int CAMERA_POST_POSITION = -1;
    private int CAMERA_FRONT_POSITION = -1;

    private float screenProp = -1.0f;

    private boolean isRecorder = false;
    private MediaRecorder mMediaRecorder;
    private String videoFileName;
    private String saveVideoPath;
    private String videoFileAbsPath;
    private Bitmap videoFirstFrame = null;

    private ErrorListener mErrorListener;

//    private ImageView mSwitchView;
//    private ImageView mFlashLamp;

    private int preview_width;
    private int preview_height;

    private int angle = 0;
    private int cameraAngle = 90;//摄像头角度   默认为90度
    private int rotation = 0;
    private byte[] firstFrame_data;

    static final int TYPE_RECORDER = 0x090;
    static final int TYPE_CAPTURE = 0x091;
    private int nowScaleRate = 0;
    private int recordScaleRate = 0;

    //视频质量
    private int mediaQuality = JCameraView.MEDIA_QUALITY_MIDDLE;
    private SensorManager sm = null;
    private boolean safeToTakePicture = false;

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
        cameraAngle = CameraParamUtil.getInstance().getCameraDisplayOrientation(context, SELECTED_CAMERA);
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
            angle = AngleUtil.getSensorAngle(values[0], values[1]);
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
        if (rotation != angle) {
            int start_rotation = 0;
            int end_rotation = 0;
            switch (rotation) {
                case 0:
                    start_rotation = 0;
                    switch (angle) {
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
                    switch (angle) {
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
                    switch (angle) {
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
                    switch (angle) {
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
            rotation = angle;
        }
    }

    void setSaveVideoPath(@Nullable String saveVideoPath) {
        if (saveVideoPath != null) {
            this.saveVideoPath = saveVideoPath;
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
        if (mParams == null) {
            mParams = mCamera.getParameters();
        }
        if (!mParams.isZoomSupported() || !mParams.isSmoothZoomSupported()) {
            return;
        }
        switch (type) {
            case TYPE_RECORDER:
                //如果不是录制视频中，上滑不会缩放
                if (!isRecorder) {
                    return;
                }
                if (zoom >= 0) {
                    //每移动50个像素缩放一个级别
                    int scaleRate = (int) (zoom / 40);
                    if (scaleRate <= mParams.getMaxZoom() && scaleRate >= nowScaleRate && recordScaleRate != scaleRate) {
                        mParams.setZoom(scaleRate);
                        mCamera.setParameters(mParams);
                        recordScaleRate = scaleRate;
                    }
                }
                break;
            case TYPE_CAPTURE:
                if (isRecorder) {
                    return;
                }
                //每移动50个像素缩放一个级别
                int scaleRate = (int) (zoom / 50);
                if (scaleRate < mParams.getMaxZoom()) {
                    nowScaleRate += scaleRate;
                    if (nowScaleRate < 0) {
                        nowScaleRate = 0;
                    } else if (nowScaleRate > mParams.getMaxZoom()) {
                        nowScaleRate = mParams.getMaxZoom();
                    }
                    mParams.setZoom(nowScaleRate);
                    mCamera.setParameters(mParams);
                }
                LogUtil.i("setZoom = " + nowScaleRate);
                break;
        }

    }

    void setMediaQuality(int quality) {
        this.mediaQuality = quality;
    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        firstFrame_data = data;
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
        saveVideoPath = "";
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
                LogUtil.e("enable shutter sound failed");
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
                e.printStackTrace();
            }
        }
        LogUtil.i("open end");
        doStartPreview(holder, screenProp);
    }

    /**
     * doStartPreview
     */
    public void doStartPreview(SurfaceHolder holder, float screenProp) {
        if (isPreviewing) {
            LogUtil.i("doStartPreview isPreviewing");
        }
        if (this.screenProp < 0) {
            this.screenProp = screenProp;
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
                preview_width = previewSize.width;
                preview_height = previewSize.height;
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
                mCamera.setDisplayOrientation(cameraAngle);//浏览角度
                mCamera.setPreviewCallback(this); //每一帧回调
                mCamera.startPreview();//启动浏览
                isPreviewing = true;
                safeToTakePicture = true;
                LogUtil.i("=== Start Preview ===");
            } catch (Exception e) {
                e.printStackTrace();
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
                isPreviewing = false;
                LogUtil.i("=== Stop Preview ===");
            } catch (Exception e) {
                e.printStackTrace();
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
                isPreviewing = false;
                mCamera.release();
                mCamera = null;
//                destroyCameraInterface();
                LogUtil.i("=== Destroy Camera ===");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            LogUtil.i( "=== Camera  Null===");
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
        switch (cameraAngle) {
            case 90:
                mNowAngle = Math.abs(angle + cameraAngle) % 360;
                break;
            case 270:
                mNowAngle = Math.abs(cameraAngle - angle);
                break;
        }
//
        LogUtil.i(angle + " = " + cameraAngle + " = " + mNowAngle);
        if (safeToTakePicture) {
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
            safeToTakePicture = false;
        }
    }

    //启动录像
    public void startRecord(Surface surface, float screenProp) {
        final int nowAngle = (angle + 90) % 360;
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            //获取第一帧图片
            Camera.Parameters parameters = mCamera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;
            YuvImage yuv = new YuvImage(firstFrame_data, parameters.getPreviewFormat(), width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);
            byte[] bytes = out.toByteArray();
            videoFirstFrame = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            Matrix matrix = new Matrix();
            if (SELECTED_CAMERA == CAMERA_POST_POSITION) {
                matrix.setRotate(nowAngle);
            } else if (SELECTED_CAMERA == CAMERA_FRONT_POSITION) {
                matrix.setRotate(270);
            }
            videoFirstFrame = Bitmap.createBitmap(videoFirstFrame, 0, 0, videoFirstFrame.getWidth(), videoFirstFrame.getHeight(), matrix, true);
        }
        if (isRecorder) {
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
        if (mParams == null) {
            mParams = mCamera.getParameters();
        }
        List<String> focusModes = mParams.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        mCamera.setParameters(mParams);
        mCamera.unlock();
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.reset();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        Camera.Size videoSize;
        if (mParams.getSupportedVideoSizes() == null) {
            videoSize = CameraParamUtil.getInstance().getPreviewSize(mParams.getSupportedPreviewSizes(), 600,
                    screenProp);
        } else {
            videoSize = CameraParamUtil.getInstance().getPreviewSize(mParams.getSupportedVideoSizes(), 600,
                    screenProp);
        }
        if (videoSize.width == videoSize.height) {
            mMediaRecorder.setVideoSize(preview_width, preview_height);
        } else {
            mMediaRecorder.setVideoSize(videoSize.width, videoSize.height);
        }
        if (SELECTED_CAMERA == CAMERA_FRONT_POSITION) {
            //手机预览倒立的处理
            if (cameraAngle == 270) {
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
            mMediaRecorder.setVideoEncodingBitRate(mediaQuality);
        } catch (Exception e) {
            mMediaRecorder.setVideoEncodingBitRate(JCameraView.MEDIA_QUALITY_FUNNY);
        }
        mMediaRecorder.setPreviewDisplay(surface);
        videoFileName = "video_" + System.currentTimeMillis() + ".mp4";
        if (saveVideoPath == null || saveVideoPath.equals("")) {
            saveVideoPath = Environment.getExternalStorageDirectory().getPath();
        }
        videoFileAbsPath = saveVideoPath + File.separator + videoFileName;
        mMediaRecorder.setOutputFile(videoFileAbsPath);
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            isRecorder = true;
        } catch (Exception e) {
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            isRecorder = false;
        }
    }

    //停止录像
    public void stopRecord(boolean isShort, StopRecordCallback callback) {
        if (!isRecorder) {
            return;
        }
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.setPreviewDisplay(null);
            try {
                mMediaRecorder.stop();
            } catch (Exception e) {
                mMediaRecorder = null;
            } finally {
                if (mMediaRecorder != null) {
                    mMediaRecorder.release();
                }
                mMediaRecorder = null;
                isRecorder = false;
            }
            LogUtil.i("MediaRecorder release.");
            if (isShort) {
                if (FileUtil.deleteFile(videoFileAbsPath)) {
                    callback.recordResult(null, null);
                }
                return;
            }
            doStopPreview();
            String fileName = saveVideoPath + File.separator + videoFileName;
            callback.recordResult(fileName, videoFirstFrame);
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
        try {
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
        void recordResult(String url, Bitmap firstFrame);
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
        if (sm == null) {
            sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        sensorEventListener = new MySensorEventListener(switchView, flashLampView);
        sm.registerListener(sensorEventListener, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager
                .SENSOR_DELAY_NORMAL);
    }

    @SuppressWarnings("ConstantConditions")
    void unregisterSensorManager(Context context) {
        if (sm == null) {
            sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        if (sensorEventListener != null)
            sm.unregisterListener(sensorEventListener);
    }

    void onPause() {
        this.isPreviewing = false;
    }
}
