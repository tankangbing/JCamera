package pony.xcode.jcamera;

public class JCameraConfig {
    private String savePath; //保存的路径
    private int mediaQuality; //录制质量
    private int features; //设置CaptureButton功能（拍照和录像）
    private int duration; //设置录制时间

    private JCameraConfig(Builder builder) {
        this.savePath = builder.savePath;
        this.mediaQuality = builder.mediaQuality;
        this.features = builder.features;
        this.duration = builder.duration;
    }

    public String getSavePath() {
        return savePath;
    }

    public int getMediaQuality() {
        return mediaQuality;
    }

    public int getFeatures() {
        return features;
    }

    public int getDuration() {
        return duration;
    }

    public static class Builder {
        private String savePath; //保存的路径
        private int mediaQuality = JCameraView.MEDIA_QUALITY_HIGH; //录制质量
        private int features; //设置CaptureButton功能（拍照和录像）
        private int duration; //设置录制时间

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

        public Builder setDuration(int duration) {
            this.duration = duration;
            return this;
        }

        public JCameraConfig build() {
            return new JCameraConfig(this);
        }
    }
}
