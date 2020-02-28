package com.holike.jcamera

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import pony.xcode.jcamera.JCamera
import pony.xcode.jcamera.JCameraActivity
import pony.xcode.jcamera.JCameraConfig
import pony.xcode.jcamera.JCameraView

class MainActivity : AppCompatActivity(), JCamera.JCameraPermissionCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        open_camera_btn.setOnClickListener {
            if (JCamera.areJCameraEnabled(this)) {
                startJCamera()
            } else {
                JCamera.requestJCamera(this, 100)
            }
        }
    }

    private fun startJCamera() {
        JCameraActivity.startCamera(this, JCameraConfig.Builder().setFeatures(JCameraView.BUTTON_STATE_ONLY_RECORDER).build(), 10086)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 100 && grantResults.isNotEmpty()) {
            JCamera.handleJCameraPermissionsResult(this, permissions, grantResults, this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10086 && resultCode == Activity.RESULT_OK && data != null) {
            val photoPath = data.getStringExtra(JCamera.CAPTURE_EXTRA);
            if (!photoPath.isNullOrEmpty()) {
                Toast.makeText(this, photoPath, Toast.LENGTH_LONG).show()
            }
            val videoPath = data.getStringExtra(JCamera.RECORD_VIDEO_EXTRA);
            if (!videoPath.isNullOrEmpty()) {
                val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra("videoPath", videoPath)
                }
                startActivity(intent)
            }
            val framePath = data.getStringExtra(JCamera.RECORD_FRAME_EXTRA);
            if (!framePath.isNullOrEmpty()) {
                Log.e("framePath", framePath)
            }
        }
    }

    override fun onJCameraPermissionDenied() {
    }

    override fun onJCameraPermissionForbid() {

    }

    override fun onJCameraPermissionGranted() {
        startJCamera()
    }
}
