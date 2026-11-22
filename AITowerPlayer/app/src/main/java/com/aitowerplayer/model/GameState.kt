package com.aitowerplayer.model

enum class GameState {
    IDLE,
    STARTING,
    GAMEPLAY,
    HOLDING,          // holding touch (safe zone)
    RELEASING,        // released because danger detected
    WIN_DETECTED,     // TOWER SMASHED screen seen
    WAITING_ANIMATION,
    CLICKING_LEVEL,   // pressing Level XX button
    POPUP_DETECTED,   // ROYAL DECREE popup
    CLICKING_SMASH,   // pressing SMASH!
    LOADING,
    FAILED,
    STOPPED
}
