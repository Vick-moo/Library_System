package com.example.librarysystem.data

import kotlinx.coroutines.delay

/**
 * Keeps the pull-to-refresh spinner on screen for a moment even when the
 * request comes back almost instantly, so it never looks like a dead button.
 */
suspend fun keepSpinnerVisible(startedAt: Long, minimumMs: Long = 700L) {
    val elapsed = System.currentTimeMillis() - startedAt
    if (elapsed < minimumMs) delay(minimumMs - elapsed)
}
