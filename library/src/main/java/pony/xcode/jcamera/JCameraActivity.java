package pony.xcode.jcamera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import pony.xcode.jcamera.listener.ClickListener;
import pony.xcode.jcamera.listener.JCameraListener;
import pony.xcode.jcamera.util.FileUtil;

//拍照或录制视频，进入页面之前需要申请相机和录音权限
public class JCameraActivity extends AppCompatActivity implements JCameraListener {

    public static void startCamera(@NonNull Activity activity, int requestCode) {
        startCamera(activity, null, requestCode);
    }

    public static void startCamera(@NonNull Activity activity, @Nullable JCameraConfig config, int requestCode) {
        activity.startActivityForResult(getJCameraIntent(activity, config), requestCode);
    }

    public static void startCamera(@NonNull Fragment fragment, int requestCode) {
        startCamera(fragment, null, requestCode);
    }

    public static void startCamera(@NonNull Fragment fragment, @Nullable JCameraConfig config, int requestCode) {
        Context context = fragment.getContext();
        if (context != null) {
            fragment.startActivityForResult(getJCameraIntent(context, config), requestCode);
        }
    }

    private static Intent getJCameraIntent(Context context, JCameraConfig config) {
        Intent intent = new Intent(context, JCameraActivity.class);
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.jcamera_bottom_in, R.anim.jcamera_bottom_silent);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mJCameraView = new JCameraView(this);
        setContentView(mJCameraView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
//        setContentView(R.layout.activity_jcamera);
//        mJCameraView = findViewById(R.id.jCameraView);
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
        final int minDuration = intent.getIntExtra("minDuration",JCameraConfig.DURATION_MIN);
        mJCameraView.setMinDuration(minDuration);
        final int maxDuration = intent.getIntExtra("maxDuration", JCameraConfig.DURATION_MAX);
        mJCameraView.setMaxDuration(maxDuration);
        mJCameraView.setJCameraListener(this);
        mJCameraView.setLeftClickListener(new ClickListener() {
            @Override
            public void onClick() {
                onBackPressed();
            }
        });
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
    protected void onResume() {
        super.onResume();
        mJCameraView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mJCameraView.onPause();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.jcamera_bottom_silent, R.anim.jcamera_bottom_out);
    }
}
