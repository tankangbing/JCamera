package pony.xcode.jcamera;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

//自定义VideoView 解决全屏不能铺满屏幕的问题
public class JCameraVideoView extends VideoView {

    public JCameraVideoView(Context context) {
        super(context);
    }

    public JCameraVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public JCameraVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 其实就是在这里做了一些处理。
        int width = getDefaultSize(0, widthMeasureSpec);
        int height = getDefaultSize(0, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
}
