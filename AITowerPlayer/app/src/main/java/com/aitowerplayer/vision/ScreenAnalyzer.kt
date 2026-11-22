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

    companion object {
        // Area bahaya = kolom tengah saja (tepat di bawah bola), bukan seluruh lebar tower
        private const val CENTER_START = 0.38f
        private const val CENTER_END = 0.62f

        // Mode api / boost: seluruh tower berubah warna emas.
        // Dari rekaman: normal 0-10% emas, mode api 60-78% emas.
        private const val FIRE_GOLD_RATIO = 0.45f
    }

    /**
     * Mode api / boost: tower berubah jadi emas -> blok hitam boleh dihancurkan.
     */
    fun isFireMode(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) return false
        val xs = (w * 0.25f).toInt()
        val xe = (w * 0.75f).toInt()
        val ys = (h * 0.35f).toInt()
        val ye = (h * 0.75f).toInt()
        var gold = 0
        var total = 0
        var y = ys
        while (y < ye) {
            var x = xs
            while (x < xe) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                if (r > 200 && g > 130 && b < 120 && (r - b) > 100) gold++
                total++
                x += 8
            }
            y += 8
        }
        return total > 0 && (gold.toFloat() / total) >= FIRE_GOLD_RATIO
    }

    data class AnalysisResult(
        val isDanger: Boolean,
        val blackRatio: Float,
        val isWinScreen: Boolean,
        val isPopup: Boolean,
        val isGameplay: Boolean,
        val isLoading: Boolean,
        val dominantHue: Float = 0f,
        val blackAhead: Float = 0f,
        val isFire: Boolean = false
    )

    fun analyze(bitmap: Bitmap): AnalysisResult {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) {
            return AnalysisResult(false, 0f, false, false, false, true)
        }

        // --- 1. Danger band analysis ---
        val bandY = ((config.detectionLinePercent / 100f) * h).toInt().coerceIn(0, h - 1)
        // Band minimal 8% tinggi layar: menutup beberapa lapisan tepat di bawah bola (bukan 1 garis tipis)
        val bandH = maxOf(config.bandHeightPx, (h * 0.08f).toInt()).coerceIn(10, h / 3)
        val yStart = (bandY - bandH / 2).coerceAtLeast(0)
        val yEnd = (bandY + bandH / 2).coerceAtMost(h - 1)

        // Sample center horizontal region (ignore edges / UI chrome)
        val xStart = (w * CENTER_START).toInt()
        val xEnd = (w * CENTER_END).toInt()
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
        val fireMode = isFireMode(bitmap)
        val isDanger = !fireMode && blackRatio >= (config.pixelThresholdPercent / 100f)

        // Lookahead: lapisan berikutnya (sedikit di bawah garis deteksi), kolom tengah saja
        val aheadCenterY = (bandY + (h * 0.03f).toInt()).coerceIn(0, h - 1)
        val aheadStart = (aheadCenterY - bandH / 2).coerceAtLeast(0)
        val aheadEnd = (aheadCenterY + bandH / 2).coerceAtMost(h - 1)
        var aheadBlack = 0
        var aheadTotal = 0
        var ay = aheadStart
        while (ay <= aheadEnd) {
            var ax = xStart
            while (ax < xEnd) {
                val ap = bitmap.getPixel(ax, ay)
                if (Color.red(ap) <= config.blackThreshold &&
                    Color.green(ap) <= config.blackThreshold &&
                    Color.blue(ap) <= config.blackThreshold
                ) {
                    aheadBlack++
                }
                aheadTotal++
                ax += stepX
            }
            ay += stepY
        }
        val blackAhead = if (aheadTotal > 0) aheadBlack.toFloat() / aheadTotal else 0f

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
        // Kartu krem di tengah layar (popup game over "Smashed by the curse" / layar hadiah)
        var cardCream = 0
        var cardTotal = 0
        var cy = (h * 0.38f).toInt()
        val cyEnd = (h * 0.55f).toInt()
        while (cy < cyEnd) {
            var cx = (w * 0.2f).toInt()
            val cxEnd = (w * 0.8f).toInt()
            while (cx < cxEnd) {
                val cp = bitmap.getPixel(cx, cy)
                val cr = Color.red(cp)
                val cg = Color.green(cp)
                val cb = Color.blue(cp)
                if (cr > 215 && cg > 185 && cb > 130 && cb < 225 && cr > cb + 20) cardCream++
                cardTotal++
                cx += 10
            }
            cy += 10
        }
        val creamCard = cardTotal > 0 && (cardCream.toFloat() / cardTotal) > 0.30f

        val isPopup = (creamRatio > 0.06f && goldRatio > 0.02f && !isWinScreen) || (creamCard && !isWinScreen)

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
            isLoading = isLoading,
            blackAhead = blackAhead,
            isFire = fireMode
        )
    }

    /**
     * Quick check only for danger (used during active hold for speed).
     */
    fun isDangerous(bitmap: Bitmap): Boolean {
        if (isFireMode(bitmap)) return false
        val w = bitmap.width
        val h = bitmap.height
        val bandY = ((config.detectionLinePercent / 100f) * h).toInt().coerceIn(0, h - 1)
        // Band minimal 8% tinggi layar: menutup beberapa lapisan tepat di bawah bola (bukan 1 garis tipis)
        val bandH = maxOf(config.bandHeightPx, (h * 0.08f).toInt()).coerceIn(10, h / 3)
        val yStart = (bandY - bandH / 2).coerceAtLeast(0)
        val yEnd = (bandY + bandH / 2).coerceAtMost(h - 1)
        val xStart = (w * CENTER_START).toInt()
        val xEnd = (w * CENTER_END).toInt()

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
