package com.enigma.bigcharts.core.utils

import androidx.compose.animation.core.*

/**
 * Shared animation specs used across chart composables.
 * Centralising them here avoids magic numbers scattered across files.
 */

/** Standard entrance tween for bar/line/pie growth animations. */
fun <T> chartEntranceTween(durationMs: Int = 800): TweenSpec<T> =
    tween(durationMillis = durationMs, easing = FastOutSlowInEasing)

/** Bouncy spring for discrete state changes (e.g. slice/bar selection). */
fun <T> chartSelectionSpring(): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/** Gentle spring for tooltip appearance. */
fun <T> tooltipSpring(): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
