/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.codelab.camera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.codelab.camera2.databinding.ActivityCameraBinding

const val TAG = "CAMERA_ACTIVITY"
const val CAMERA_PERMISSION_REQUEST_CODE = 1992

open class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var camera2Session: Camera2Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        camera2Session = Camera2Session.newInstance(this, binding.textureView, requestPermission = {
            requestPermission()
        })

        val windowParams: WindowManager.LayoutParams = window.attributes
        windowParams.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
        window.attributes = windowParams
        binding.ivSwitch.setOnClickListener {
           camera2Session.switchCamera()
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
                camera2Session.openCamera()
            } else {
                finish()
            }
        }
    }
}
