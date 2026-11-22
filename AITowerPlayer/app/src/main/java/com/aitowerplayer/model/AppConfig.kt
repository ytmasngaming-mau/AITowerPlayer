package com.aitowerplayer.model

data class AppConfig(
    // Detection
    var detectionLinePercent: Float = 38f,      // Y position of detection band (% of screen height)
    var blackThreshold: Int = 40,               // RGB max for "black" pixel
    var pixelThresholdPercent: Float = 25f,     // % of black pixels in band to consider danger
    var bandHeightPx: Int = 40,                 // height of analysis band in pixels

    // Performance
    var analysisFps: Int = 15,
    var animationWaitMs: Long = 1500L,

    // Touch
    var touchXPercent: Float = 50f,             // center X by default
    var touchYPercent: Float = 55f,             // lower part of tower

    // Auto Level
    var autoLevelEnabled: Boolean = true,
    var useOcr: Boolean = false,

    // Timeouts
    var winDetectTimeoutMs: Long = 8000L,
    var clickVerifyTimeoutMs: Long = 3000L,
    var maxHoldMs: Long = 30000L                // safety max hold
)
