package pony.xcode.jcamera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class JCamera {
    public static final String CAPTURE_EXTRA = "capture-extra"; //拍照返回
    public static final String RECORD_VIDEO_EXTRA = "record-video-extra"; //录制视频返回的路径
    public static final String RECORD_FRAME_EXTRA = "record-frame-extra";  //录制的视频第一帧图片路径
    private Activity mActivity;
    private Fragment mFragment;
    private int mThemeId;
    private JCameraConfig mConfig;
    private int mRequestCode = 999;

    private JCamera(Activity activity) {
        this.mActivity = activity;
    }

    private JCamera(Fragment fragment) {
        this.mFragment = fragment;
    }

    public static JCamera with(Activity activity) {
        return new JCamera(activity);
    }

    public static JCamera with(Fragment fragment) {
        return new JCamera(fragment);
    }

    public JCamera themeId(@StyleRes int themeId) {
        this.mThemeId = themeId;
        return this;
    }

    public JCamera config(@Nullable JCameraConfig config) {
        this.mConfig = config;
        return this;
    }

    public JCamera requestCode(@IntRange(from = 0, to = 65536) int requestCode) {
        this.mRequestCode = requestCode;
        return this;
    }

    public void start() {
        if (mActivity != null) {
            JCameraActivity.startCamera(mActivity, mThemeId, mConfig, mRequestCode);
        } else {
            JCameraActivity.startCamera(mFragment, mThemeId, mConfig, mRequestCode);
        }
    }

    //录制视频要申请相机和录音的权限
    public static boolean areJCameraEnabled(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestJCamera(@NonNull Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, requestCode);
    }

    public static void requestJCamera(@NonNull Fragment fragment, int requestCode) {
        fragment.requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, requestCode);
    }

    public static void handleJCameraPermissionsResult(@NonNull Activity activity, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults, @Nullable JCameraPermissionCallback callback) {
        if (callback == null) return;
        if (isJCameraPermissionsGranted(grantResults)) {
            callback.onJCameraPermissionGranted();
        } else {
            boolean forbid = false;
            for (String permission : permissions) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    forbid = true;
                    break;
                }
            }
            if (forbid) {
                callback.onJCameraPermissionForbid();
            } else {
                callback.onJCameraPermissionDenied();
            }
        }
    }

    public static void handleJCameraPermissionsResult(@NonNull Fragment fragment, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults, @Nullable JCameraPermissionCallback callback) {
        if (callback == null) return;
        if (isJCameraPermissionsGranted(grantResults)) {
            callback.onJCameraPermissionGranted();
        } else {
            boolean forbid = false;
            for (String permission : permissions) {
                if (!fragment.shouldShowRequestPermissionRationale(permission)) {
                    forbid = true;
                    break;
                }
            }
            if (forbid) {
                callback.onJCameraPermissionForbid();
            } else {
                callback.onJCameraPermissionDenied();
            }
        }
    }

    private static boolean isJCameraPermissionsGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public interface JCameraPermissionCallback {

        void onJCameraPermissionForbid();  //用户禁止了相机或录音权限-应该提示用户手动打开（录制视频同时需要相机和录音权限）

        void onJCameraPermissionDenied(); //用户没有禁止相机和录音权限，暂时“不允许”-此回调方法里可以提示用户再次申请权限

        void onJCameraPermissionGranted(); //相机和录音权限均已授予
    }
}
