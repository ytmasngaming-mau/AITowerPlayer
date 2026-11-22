package com.aitowerplayer.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.aitowerplayer.model.AppConfig
import com.aitowerplayer.util.LogManager
import kotlin.math.abs

/**
 * Computer vision analyzer for Tower Smash.
 *
 * Detects:
 * - Dangerous black zones in a horizontal band
 * - Win screen (TOWER SMASHED / stars / gold tones)
 * - ROYAL DECREE popup (cream dialog + orange button)
 * - Loading / transition screens
 */
class ScreenAnalyzer(private var config: AppConfig) {

    fun updateConfig(c: AppConfig) {
        config = c
    }

    data class AnalysisResult(
        val isDanger: Boolean,
        val blackRatio: Float,
        val isWinScreen: Boolean,
        val isPopup: Boolean,
        val isGameplay: Boolean,
        val isLoading: Boolean,
        val dominantHue: Float = 0f
    )

    fun analyze(bitmap: Bitmap): AnalysisResult {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) {
            return AnalysisResult(false, 0f, false, false, false, true)
        }

        // --- 1. Danger band analysis ---
        val bandY = ((config.detectionLinePercent / 100f) * h).toInt().coerceIn(0, h - 1)
        val bandH = config.bandHeightPx.coerceIn(10, h / 3)
        val yStart = (bandY - bandH / 2).coerceAtLeast(0)
        val yEnd = (bandY + bandH / 2).coerceAtMost(h - 1)

        // Sample center horizontal region (ignore edges / UI chrome)
        val xStart = (w * 0.15f).toInt()
        val xEnd = (w * 0.85f).toInt()
        val stepX = 4
        val stepY = 3

        var blackCount = 0
        var total = 0
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L

        var y = yStart
        while (y <= yEnd) {
            var x = xStart
            while (x < xEnd) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                rSum += r
                gSum += g
                bSum += b
                // Black / very dark pixel
                if (r <= config.blackThreshold && g <= config.blackThreshold && b <= config.blackThreshold) {
                    blackCount++
                }
                total++
                x += stepX
            }
            y += stepY
        }

        val blackRatio = if (total > 0) blackCount.toFloat() / total else 0f
        val isDanger = blackRatio >= (config.pixelThresholdPercent / 100f)

        // --- 2. Full-screen color stats for state detection ---
        val sampleStep = 12
        var sampleCount = 0
        var brightCount = 0
        var goldCount = 0
        var purpleCount = 0
        var creamCount = 0
        var blueCount = 0
        var darkCount = 0
        var redBannerCount = 0

        // Sample upper half (win banner / stars) and center
        val regions = listOf(
            // Top center (banner area)
            Triple((w * 0.2f).toInt(), (h * 0.08f).toInt(), (w * 0.8f).toInt() to (h * 0.35f).toInt()),
            // Center (stars / coin)
            Triple((w * 0.25f).toInt(), (h * 0.30f).toInt(), (w * 0.75f).toInt() to (h * 0.55f).toInt()),
            // Lower center (buttons)
            Triple((w * 0.2f).toInt(), (h * 0.60f).toInt(), (w * 0.8f).toInt() to (h * 0.85f).toInt())
        )

        for ((rx, ry, end) in regions) {
            val (ex, ey) = end
            var yy = ry
            while (yy < ey) {
                var xx = rx
                while (xx < ex) {
                    val p = bitmap.getPixel(xx, yy)
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)
                    sampleCount++

                    if (r > 200 && g > 200 && b > 200) brightCount++
                    // Gold / yellow (stars, coins, Level button)
                    if (r > 180 && g > 140 && b < 100) goldCount++
                    // Purple / dark blue background of win screen
                    if (r < 80 && g < 60 && b > 100) purpleCount++
                    // Cream / beige popup card
                    if (r > 200 && g > 180 && b > 140 && b < 200) creamCount++
                    // Cyan / light blue tower
                    if (b > r + 30 && b > g && b > 120) blueCount++
                    // Very dark
                    if (r < 40 && g < 40 && b < 40) darkCount++
                    // Red banner
                    if (r > 180 && g < 80 && b < 80) redBannerCount++

                    xx += sampleStep
                }
                yy += sampleStep
            }
        }

        val sc = sampleCount.coerceAtLeast(1).toFloat()
        val goldRatio = goldCount / sc
        val purpleRatio = purpleCount / sc
        val creamRatio = creamCount / sc
        val blueRatio = blueCount / sc
        val darkRatio = darkCount / sc
        val redRatio = redBannerCount / sc
        val brightRatio = brightCount / sc

        // Win screen: purple bg + gold stars + red banner
        val isWinScreen = purpleRatio > 0.12f && goldRatio > 0.04f && (redRatio > 0.01f || brightRatio > 0.05f)

        // Popup: cream card + some gold/orange button area, darker surroundings
        val isPopup = creamRatio > 0.06f && goldRatio > 0.02f && !isWinScreen

        // Gameplay: significant blue tower + not win/popup
        val isGameplay = blueRatio > 0.08f && !isWinScreen && !isPopup

        // Loading: mostly dark or uniform
        val isLoading = darkRatio > 0.55f || (brightRatio < 0.02f && blueRatio < 0.03f && !isWinScreen && !isPopup)

        return AnalysisResult(
            isDanger = isDanger,
            blackRatio = blackRatio,
            isWinScreen = isWinScreen,
            isPopup = isPopup,
            isGameplay = isGameplay,
            isLoading = isLoading
        )
    }

    /**
     * Quick check only for danger (used during active hold for speed).
     */
    fun isDangerous(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val bandY = ((config.detectionLinePercent / 100f) * h).toInt().coerceIn(0, h - 1)
        val bandH = config.bandHeightPx.coerceIn(10, h / 3)
        val yStart = (bandY - bandH / 2).coerceAtLeast(0)
        val yEnd = (bandY + bandH / 2).coerceAtMost(h - 1)
        val xStart = (w * 0.2f).toInt()
        val xEnd = (w * 0.8f).toInt()

        var black = 0
        var total = 0
        val step = 5
        var y = yStart
        while (y <= yEnd) {
            var x = xStart
            while (x < xEnd) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) <= config.blackThreshold &&
                    Color.green(p) <= config.blackThreshold &&
                    Color.blue(p) <= config.blackThreshold
                ) {
                    black++
                }
                total++
                x += step
            }
            y += step
        }
        return total > 0 && (black.toFloat() / total) >= (config.pixelThresholdPercent / 100f)
    }
}
