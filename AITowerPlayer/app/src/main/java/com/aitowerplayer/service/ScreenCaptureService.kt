package com.aitowerplayer.service

import kotlin.random.Random
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aitowerplayer.AITowerApp
import com.aitowerplayer.R
import com.aitowerplayer.model.GameState
import com.aitowerplayer.ui.MainActivity
import com.aitowerplayer.util.ConfigManager
import com.aitowerplayer.util.LogManager
import com.aitowerplayer.vision.ScreenAnalyzer
import kotlinx.coroutines.*

/**
 * Foreground service that captures the screen via MediaProjection
 * and runs the AI state machine.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.aitowerplayer.START"
        const val ACTION_STOP = "com.aitowerplayer.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var currentState: GameState = GameState.IDLE
            private set

        var stateListener: ((GameState, String) -> Unit)? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private lateinit var analyzer: ScreenAnalyzer
    private var aiJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var isHoldingTouch = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var holdStartTime = 0L
    private var safeStreak = 0
    private var lastHoldAt = System.currentTimeMillis()
    private var lastStepAt = 0L
    // MODE LANGKAH: lama satu ketukan (ms) dan jeda tunggu setelahnya sebelum cek lagi (ms).
    // Kalau 1 ketukan menghancurkan >1 lapisan: kecilkan stepTapMs (mis. 60). Kalau 0 lapisan: besarkan (mis. 110).
    private val stepTapMs = 80L
    private val stepSettleMs = 230L
    private var lastFire = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        analyzer = ScreenAnalyzer(ConfigManager.config)
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        LogManager.log("ScreenCaptureService dibuat. Resolusi: ${screenWidth}x${screenHeight}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (data != null && resultCode == Activity.RESULT_OK) {
                    startCapture(resultCode, data)
                } else {
                    LogManager.log("Gagal: data MediaProjection tidak valid.")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopAi()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        startForegroundNotification()
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                LogManager.log("MediaProjection dihentikan oleh sistem.")
                stopAi()
                stopSelf()
            }
        }, mainHandler)

        captureThread = HandlerThread("CaptureThread").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AITowerCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, captureHandler
        )

        isRunning = true
        setState(GameState.STARTING, "Memulai AI…")
        LogManager.log("Screen capture aktif. Memulai AI loop.")
        startAiLoop()
    }

    private fun startForegroundNotification() {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, AITowerApp.CHANNEL_ID)
            .setContentTitle("AI Tower Player")
            .setContentText(getString(R.string.notification_running))
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(pi)
            .addAction(R.drawable.ic_stop, "STOP", stopPi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun startAiLoop() {
        aiJob?.cancel()
        aiJob = scope.launch {
            val config = ConfigManager.config
            analyzer.updateConfig(config)
            val frameInterval = (1000L / config.analysisFps.coerceIn(5, 30))

            // Initial delay to let game settle
            delay(800)
            setState(GameState.GAMEPLAY, "Gameplay dimulai")

            while (isActive && isRunning) {
                val loopStart = System.currentTimeMillis()
                try {
                    val bitmap = captureFrame()
                    if (bitmap != null) {
                        processFrame(bitmap)
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    LogManager.log("Error frame: ${e.message}")
                }
                // Kurangi waktu proses dari jeda, supaya reaksi secepat mungkin
                delay((frameInterval - (System.currentTimeMillis() - loopStart)).coerceAtLeast(1L))
            }
        }
    }

    private fun captureFrame(): Bitmap? {
        val image: Image = imageReader?.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            if (rowPadding == 0) {
                bitmap
            } else {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        val config = ConfigManager.config
        val result = analyzer.analyze(bitmap)
        val acc = TowerAccessibilityService.instance

        when (currentState) {
            GameState.GAMEPLAY, GameState.HOLDING, GameState.RELEASING -> {
                // Check win first
                if (result.isWinScreen && config.autoLevelEnabled) {
                    releaseTouch()
                    setState(GameState.WIN_DETECTED, "TOWER SMASHED terdeteksi!")
                    ConfigManager.incrementWin()
                    delay(config.animationWaitMs)
                    handleWin()
                    return
                }
                if (result.isPopup && config.autoLevelEnabled) {
                    releaseTouch()
                    setState(GameState.POPUP_DETECTED, "Popup ROYAL DECREE terdeteksi")
                    delay(600)
                    handlePopup()
                    return
                }

                // Mode api (tower emas): hitam boleh dihancurkan
                if (result.isFire != lastFire) {
                    lastFire = result.isFire
                    LogManager.log(if (lastFire) "MODE API aktif - hajar semuanya" else "Mode api selesai")
                }

                // MODE LANGKAH: satu ketukan pendek = satu lapisan, lalu berhenti dan cek lagi.
                // Hanya saat mode api (tower emas) jari ditahan terus karena hitam boleh dihancurkan.
                val now = System.currentTimeMillis()
                if (result.isDanger) {
                    // Ada hitam di lapisan dekat bola: jangan ketuk, tunggu hitamnya bergeser
                    safeStreak = 0
                    if (isHoldingTouch) {
                        releaseTouch()
                        setState(
                            GameState.RELEASING,
                            "Ada hitam dekat - lepas (bawah=${"%.0f".format(result.blackRatio * 100)}%)"
                        )
                    }
                } else {
                    safeStreak++
                    if (result.isFire) {
                        if (!isHoldingTouch) {
                            startTouch()
                            setState(GameState.HOLDING, "Mode api - tahan")
                        } else if (now - holdStartTime > config.maxHoldMs) {
                            releaseTouch()
                        }
                    } else if (isHoldingTouch) {
                        // Sisa dari mode api: lepas dulu
                        releaseTouch()
                        safeStreak = 0
                        lastStepAt = now
                    } else if (safeStreak >= 2 && now - lastStepAt >= stepSettleMs &&
                        (result.isGameplay || !result.isLoading)
                    ) {
                        delay(Random.nextLong(15L, 40L))
                        stepTap()
                        safeStreak = 0
                        setState(
                            GameState.HOLDING,
                            "Langkah: hancurkan 1 lapisan (black=${"%.0f".format(result.blackRatio * 100)}%)"
                        )
                    }
                }
            }

            GameState.WIN_DETECTED, GameState.WAITING_ANIMATION -> {
                // already handled in delay path
            }

            GameState.CLICKING_LEVEL -> {
                // wait for verification
            }

            GameState.POPUP_DETECTED, GameState.CLICKING_SMASH -> {
                // handled in dedicated methods
            }

            GameState.LOADING -> {
                if (result.isGameplay && !result.isLoading) {
                    setState(GameState.GAMEPLAY, "Kembali ke gameplay")
                }
            }

            else -> {}
        }
    }

    /** Satu ketukan pendek (menghancurkan sekitar satu lapisan). */
    private fun stepTap() {
        val acc = TowerAccessibilityService.instance ?: run {
            LogManager.log("Accessibility tidak aktif!")
            return
        }
        val config = ConfigManager.config
        val x = (screenWidth * (config.touchXPercent / 100f) + Random.nextInt(-25, 26))
            .coerceIn(1f, (screenWidth - 1).toFloat())
        val y = (screenHeight * (config.touchYPercent / 100f) + Random.nextInt(-40, 41))
            .coerceIn(1f, (screenHeight - 1).toFloat())
        acc.tap(x, y, stepTapMs)
        lastStepAt = System.currentTimeMillis()
    }

    private fun startTouch() {
        val acc = TowerAccessibilityService.instance ?: run {
            LogManager.log("Accessibility tidak aktif!")
            return
        }
        val config = ConfigManager.config
        // Posisi sentuhan sedikit acak supaya terasa seperti jari manusia
        lastTouchX = (screenWidth * (config.touchXPercent / 100f) + Random.nextInt(-25, 26))
            .coerceIn(1f, (screenWidth - 1).toFloat())
        lastTouchY = (screenHeight * (config.touchYPercent / 100f) + Random.nextInt(-40, 41))
            .coerceIn(1f, (screenHeight - 1).toFloat())
        acc.startHold(lastTouchX, lastTouchY)
        isHoldingTouch = true
        holdStartTime = System.currentTimeMillis()
    }

    private fun releaseTouch() {
        if (!isHoldingTouch) return
        TowerAccessibilityService.instance?.releaseHold()
        isHoldingTouch = false
    }

    private suspend fun handleWin() {
        setState(GameState.CLICKING_LEVEL, "Menekan tombol Level…")
        val tasks = ConfigManager.tasks
            .filter { it.enabled && it.trigger == "AFTER_WIN" }
            .sortedBy { it.order }

        if (tasks.isNotEmpty()) {
            for (task in tasks) {
                delay(task.delayMs)
                val (px, py) = resolveTaskPoint(task)
                performTap(px, py)
                LogManager.log("Task [${task.name}] diklik di (${px.toInt()},${py.toInt()}) percent=${task.usePercent}")
            }
        } else {
            // Default sesuai screenshot: tombol Level di ~78% tinggi layar
            delay(1000)
            performTap(screenWidth / 2f, screenHeight * 0.78f)
        }

        // Lanjutkan melewati layar hadiah / Collect / restart sampai gameplay lagi
        delay(1200)
        advanceScreens()
    }

    private suspend fun handlePopup() {
        setState(GameState.CLICKING_SMASH, "Popup terdeteksi - lanjutkan")
        advanceScreens()
    }

    /**
     * Loop pemulih untuk semalaman: selama bukan layar gameplay, ketuk tombol yang diketahui
     * secara bergantian (Restart tower, Level, SMASH), dan berhenti kalau gameplay sudah stabil.
     * Tombol hadiah / Collect / iklan sengaja tidak diketuk.
     */
    private suspend fun advanceScreens() {
        setState(GameState.LOADING, "Melewati layar hadiah / restart...")
        val names = listOf("Restart tower", "Level", "SMASH")
        val ys = listOf(58f, 81f, 52f)
        var gameplayStreak = 0
        var idx = 0
        val startMs = System.currentTimeMillis()
        while (isRunning && System.currentTimeMillis() - startMs < 60000L) {
            val bmp = captureFrame()
            if (bmp == null) {
                delay(300)
                continue
            }
            val r = analyzer.analyze(bmp)
            bmp.recycle()
            if (r.isGameplay && !r.isWinScreen && !r.isPopup) {
                gameplayStreak++
                if (gameplayStreak >= 3) {
                    safeStreak = 0
                    lastHoldAt = System.currentTimeMillis()
                    setState(GameState.GAMEPLAY, "Gameplay dimulai lagi")
                    return
                }
                delay(300)
                continue
            }
            gameplayStreak = 0
            val i = idx % names.size
            idx++
            performTap(screenWidth / 2f, screenHeight * (ys[i] / 100f))
            LogManager.log("Lanjut layar: tap ${names[i]} (50%, ${ys[i].toInt()}%)")
            delay(1300)
        }
        safeStreak = 0
        lastHoldAt = System.currentTimeMillis()
        setState(GameState.GAMEPLAY, "Timeout lanjut layar - coba main lagi")
    }

    /**
     * Ubah task X/Y menjadi pixel.
     * Jika usePercent=true → x,y = 0..100 persen layar.
     * Jika usePercent=false → x,y = pixel absolut.
     */
    private fun resolveTaskPoint(task: com.aitowerplayer.model.TaskItem): Pair<Float, Float> {
        return if (task.usePercent) {
            val px = screenWidth * (task.x.coerceIn(0, 100) / 100f)
            val py = screenHeight * (task.y.coerceIn(0, 100) / 100f)
            px to py
        } else {
            task.x.toFloat() to task.y.toFloat()
        }
    }

    private fun performTap(x: Float, y: Float) {
        val acc = TowerAccessibilityService.instance
        if (acc == null) {
            LogManager.log("Tidak bisa tap: Accessibility off")
            return
        }
        acc.tap(x, y)
    }

    private fun setState(state: GameState, msg: String) {
        currentState = state
        LogManager.log("[$state] $msg")
        mainHandler.post {
            stateListener?.invoke(state, msg)
        }
    }

    private fun stopAi() {
        isRunning = false
        releaseTouch()
        aiJob?.cancel()
        aiJob = null
        setState(GameState.STOPPED, "AI dihentikan")
        LogManager.log("AI dihentikan sepenuhnya.")
    }

    override fun onDestroy() {
        stopAi()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        captureThread?.quitSafely()
        scope.cancel()
        isRunning = false
        currentState = GameState.IDLE
        super.onDestroy()
    }
}
