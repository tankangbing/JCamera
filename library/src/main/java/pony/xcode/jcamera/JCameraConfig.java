package pony.xcode.jcamera;

public class JCameraConfig {
    static final int DURATION_MIN = 1500;  //默认最小录制时长
    static final int DURATION_MAX = 15 * 1000; //默认最大录制时长
    static final int QUALITY = JCameraView.MEDIA_QUALITY_HIGH; //默认拍摄质量
    static final int FEATURES = JCameraView.BUTTON_STATE_BOTH; //默认模式-点击拍照长按摄像

    private String savePath; //保存的路径
    private int mediaQuality; //录制质量
    private int features; //设置CaptureButton功能（拍照和录像）
    private int minDuration; //最小录制时间
    private int maxDuration; //设置录制时间

    private JCameraConfig(Builder builder) {
        this.savePath = builder.savePath;
        this.mediaQuality = builder.mediaQuality;
        this.features = builder.features;
        this.minDuration = builder.minDuration;
        this.maxDuration = builder.maxDuration;
    }

    String getSavePath() {
        return savePath;
    }

    int getMediaQuality() {
        return mediaQuality;
    }

    int getFeatures() {
        return features;
    }

    int getMinDuration() {
        return minDuration;
    }

    int getMaxDuration() {
        return maxDuration;
    }

    public static class Builder {
        private String savePath; //保存的路径
        private int mediaQuality = JCameraView.MEDIA_QUALITY_HIGH; //录制质量
        private int features; //设置CaptureButton功能（拍照和录像）
        private int minDuration; //设置最小录制时间
        private int maxDuration; //设置录制时间

        public Builder setPath(String savePath) {
            this.savePath = savePath;
            return this;
        }

        public Builder setMediaQuality(int mediaQuality) {
            this.mediaQuality = mediaQuality;
            return this;
        }

        public Builder setFeatures(int features) {
            this.features = features;
            return this;
        }

        public Builder setMinDuration(int minDuration) {
            this.minDuration = minDuration;
            return this;
        }

        public Builder setMaxDuration(int maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public JCameraConfig build() {
            return new JCameraConfig(this);
        }
    }
}
