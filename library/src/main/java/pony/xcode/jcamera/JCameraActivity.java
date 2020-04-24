package pony.xcode.jcamera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import pony.xcode.jcamera.listener.ErrorListener;
import pony.xcode.jcamera.listener.JCameraListener;
import pony.xcode.jcamera.util.FileUtil;

//拍照或录制视频，进入页面之前需要申请相机和录音权限
public class JCameraActivity extends AppCompatActivity implements JCameraListener, JCamera.JCameraPermissionCallback {

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //适配android5.0以下
            /*解决低版本手机vectorDrawable不支持儿闪退问题*/
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        }
    }

    static void startCamera(@NonNull Activity activity, @StyleRes int themeId, @Nullable JCameraConfig config, int requestCode) {
        activity.startActivityForResult(getJCameraIntent(activity, themeId, config), requestCode);
    }

    static void startCamera(@NonNull Fragment fragment, @StyleRes int themeId, @Nullable JCameraConfig config, int requestCode) {
        Context context = fragment.getContext();
        if (context != null) {
            fragment.startActivityForResult(getJCameraIntent(context, themeId, config), requestCode);
        }
    }

    private static Intent getJCameraIntent(Context context, int themeId, JCameraConfig config) {
        Intent intent = new Intent(context, JCameraActivity.class);
        intent.putExtra("themeId", themeId);
        if (config != null) {
            intent.putExtra("path", config.getSavePath());
            intent.putExtra("quality", config.getMediaQuality());
            intent.putExtra("features", config.getFeatures());
            intent.putExtra("minDuration", config.getMinDuration());
            intent.putExtra("maxDuration", config.getMaxDuration());
        }
        return intent;
    }

    private JCameraView mJCameraView;
    private boolean mUserSetting;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        //强制竖屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        int themeId = getIntent().getIntExtra("themeId", 0);
        if (themeId != 0) {
            setTheme(themeId);
        }
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.jcamera_bottom_in, R.anim.jcamera_bottom_silent);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (JCamera.areJCameraEnabled(this)) {
            initialize();
        } else {
            requestJCamera();
        }
    }

    private void requestJCamera() {
        JCamera.requestJCamera(this, 999);
    }

    private void initialize() {
        mJCameraView = new JCameraView(this);
        setContentView(mJCameraView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        Intent intent = getIntent();
        String path = intent.getStringExtra("path");
        if (TextUtils.isEmpty(path)) {
            path = FileUtil.generatePath(this);
        }
        mJCameraView.setSaveVideoPath(path);
        int mediaQuality = intent.getIntExtra("quality", JCameraConfig.QUALITY);
        if (mediaQuality <= 0) {
            mediaQuality = JCameraView.MEDIA_QUALITY_HIGH;
        }
        mJCameraView.setMediaQuality(mediaQuality);
        final int features = intent.getIntExtra("features", JCameraConfig.FEATURES);
        mJCameraView.setFeatures(features);
        final int minDuration = intent.getIntExtra("minDuration", JCameraConfig.DURATION_MIN);
        mJCameraView.setMinDuration(minDuration);
        final int maxDuration = intent.getIntExtra("maxDuration", JCameraConfig.DURATION_MAX);
        mJCameraView.setMaxDuration(maxDuration);
        mJCameraView.setJCameraListener(this);
        mJCameraView.setReturnListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        mJCameraView.setErrorListener(new ErrorListener() {
            @Override
            public void onError() {
                onCameraError();
            }
        });
    }

    //启用相机失败
    protected void onCameraError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.jcamera_dialog_title)
                .setMessage(R.string.jcamera_error)
                .setPositiveButton(R.string.jcamera_close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        }).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        JCamera.handleJCameraPermissionsResult(this, permissions, grantResults, this);
    }

    @Override
    public void onJCameraPermissionGranted() {
        initialize();
    }

    //某一权限“不允许”
    @Override
    public void onJCameraPermissionDenied() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.jcamera_dialog_title)
                .setMessage(R.string.jcamera_permissions_dismiss)
                .setNegativeButton(R.string.jcamera_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                }).setPositiveButton(R.string.jcamera_open, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                requestJCamera();  //重新去申请权限
            }
        }).show();
    }

    //某一权限已被禁止回调
    @Override
    public void onJCameraPermissionForbid() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.jcamera_dialog_title)
                .setMessage(R.string.jcamera_permissions_forbid)
                .setNegativeButton(R.string.jcamera_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                }).setPositiveButton(R.string.jcamera_open, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                startAppSettings(JCameraActivity.this);
            }
        }).show();
    }

    //前往app设置页面
    private void startAppSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getApplicationContext().getPackageName()));
            activity.startActivity(intent);
            mUserSetting = true;
        } catch (Exception ignored) {
        }
    }

    @Override
    public void captureSuccess(Bitmap bitmap) {  //拍照成功返回
        Intent intent = new Intent();
        intent.putExtra(JCamera.CAPTURE_EXTRA, FileUtil.convert(FileUtil.generateCapturePath(this), bitmap));
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void recordSuccess(String url, Bitmap firstFrame) {  //录制视频成功返回
        Intent intent = new Intent();
        intent.putExtra(JCamera.RECORD_VIDEO_EXTRA, url);
        intent.putExtra(JCamera.RECORD_FRAME_EXTRA, FileUtil.convert(FileUtil.generatePath(this), firstFrame));
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mUserSetting) {
            if (!JCamera.areJCameraEnabled(this)) {
                finish();
            } else {
                initialize();
            }
            mUserSetting = false;
        } else {
            if (JCamera.areJCameraEnabled(this)) {
                initialize();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mJCameraView != null) {
            mJCameraView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mJCameraView != null) {
            mJCameraView.onPause();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.jcamera_bottom_silent, R.anim.jcamera_bottom_out);
    }

    @Override
    protected void onDestroy() {
        if (mJCameraView != null) {
            mJCameraView.onDestroy();
        }
        super.onDestroy();
    }
}
