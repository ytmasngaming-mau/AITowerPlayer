package com.aitowerplayer.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.aitowerplayer.R
import com.aitowerplayer.util.LogManager

class FloatingControlService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_control, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val btnStartStop = floatingView!!.findViewById<ImageButton>(R.id.btnFloatStartStop)
        val btnClose = floatingView!!.findViewById<ImageButton>(R.id.btnFloatClose)

        updatePlayIcon(btnStartStop)

        btnStartStop.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                val stop = Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)
                startService(stop)
                btnStartStop.setImageResource(R.drawable.ic_play)
                LogManager.log("Stop dari floating control")
            } else {
                // User must start from MainActivity to grant MediaProjection
                LogManager.log("Mulai AI dari aplikasi utama (butuh izin screen capture)")
            }
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        // Drag
        floatingView!!.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params!!.x = initialX + (event.rawX - touchX).toInt()
                        params!!.y = initialY + (event.rawY - touchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(floatingView, params)
        LogManager.log("Floating control ditampilkan")
    }

    private fun updatePlayIcon(btn: ImageButton) {
        if (ScreenCaptureService.isRunning) {
            btn.setImageResource(R.drawable.ic_stop)
        } else {
            btn.setImageResource(R.drawable.ic_play)
        }
    }

    override fun onDestroy() {
        floatingView?.let { windowManager?.removeView(it) }
        floatingView = null
        super.onDestroy()
    }
}
