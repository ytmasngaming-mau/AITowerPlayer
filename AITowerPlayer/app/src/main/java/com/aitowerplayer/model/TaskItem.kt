package com.aitowerplayer.model

import java.util.UUID

/**
 * Task klik otomatis.
 *
 * Jika [usePercent] = true, [x] dan [y] diisi 0–100 (persen lebar/tinggi layar).
 * Jika false, [x]/[y] adalah pixel absolut.
 *
 * Default dari layout screenshot Tower Smash:
 * - Level button (layar TOWER SMASHED): tengah bawah ~50%, 78%
 * - SMASH button (popup ROYAL DECREE): tengah dialog ~50%, 52%
 */
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Task",
    var x: Int = 50,
    var y: Int = 50,
    var delayMs: Long = 800L,
    var order: Int = 1,
    var enabled: Boolean = true,
    /** AFTER_WIN | AFTER_POPUP | ON_FAIL | MANUAL */
    var trigger: String = "AFTER_WIN",
    /** true = x,y adalah persen (0-100), false = pixel */
    var usePercent: Boolean = true
)
