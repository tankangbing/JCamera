package com.holike.jcamera

import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_videoplayer.*

class VideoPlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_videoplayer)
        val videoPath = intent.getStringExtra("videoPath")
        videoView.setVideoPath(videoPath)
        videoView.setOnPreparedListener { mp ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            }
            mp.setOnVideoSizeChangedListener { _, width, height ->
                updateVideoViewSize(width, height)
            }
            mp.start()
        }
        videoView.setOnCompletionListener { m ->
            m.stop()
            finish()
        }
    }

    private fun updateVideoViewSize(videoWidth: Int, videoHeight: Int) {
        if (videoWidth > videoHeight) {
            val videoViewParam: FrameLayout.LayoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, videoHeight)
            videoViewParam.gravity = Gravity.CENTER
            videoView.layoutParams = videoViewParam
        }
    }

    override fun onResume() {
        super.onResume()
        videoView.resume()
    }

    override fun onPause() {
        super.onPause()
        videoView.pause()
    }

    override fun onDestroy() {
        videoView.stopPlayback()
        super.onDestroy()
    }
}