package com.mitra.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Foundational visual tokens. Every screen consumes from here. Source of truth lives in
 * docs/design/tokens.md — keep this file in lockstep with that doc.
 *
 * Aesthetic anchor: Linear / Things / Notion / Bear / Apple Notes. Calm, paper-like, capable but
 * quiet. NOT futuristic, neon, sci-fi, gradient-heavy, or "AI flashy."
 *
 * Brand truth: privacy, locality, friendship (मित्र). The visuals should feel like a notebook,
 * not a control panel.
 */
object MitraTokens {
    /** Warm parchment-cream light palette. Outline > shadow for separation. */
    object Light {
        val bg = Color(0xFFFAF7F2)
        val surface = Color(0xFFFFFFFF)
        val surfaceVariant = Color(0xFFF0EBE2)
        val primary = Color(0xFFA85339)
        val onPrimary = Color(0xFFFFFFFF)
        val onSurface = Color(0xFF1F1C1A)
        val onSurfaceVariant = Color(0xFF5C5853)
        val outline = Color(0xFFD4CDC2)
        val success = Color(0xFF4F7A3F)
        val warning = Color(0xFF8B6914)
        val danger = Color(0xFF962F2F)
        val info = Color(0xFF3D5E7A)

        // Material 3 container slots (not in tokens.md — plumbing only). Derived to keep
        // the clay character without introducing a second accent.
        val primaryContainer = Color(0xFFEAD3C9)
        val onPrimaryContainer = Color(0xFF3D1F12)
    }

    /** Warm dusk-paper dark palette. Tint > shadow for hierarchy. No pure black. */
    object Dark {
        val bg = Color(0xFF1A1715)
        val surface = Color(0xFF221E1B)
        val surfaceVariant = Color(0xFF2D2925)
        val primary = Color(0xFFD08561)
        val onPrimary = Color(0xFF1A1715)
        val onSurface = Color(0xFFF0EAE1)
        val onSurfaceVariant = Color(0xFFA8A199)
        val outline = Color(0xFF3D3833)
        val success = Color(0xFF8FB97D)
        val warning = Color(0xFFD4A852)
        val danger = Color(0xFFD9706C)
        val info = Color(0xFF7AA0C2)

        val primaryContainer = Color(0xFF4A2D24)
        val onPrimaryContainer = Color(0xFFF5DDD2)
    }

    // ---- Spacing (4-pt grid) ----------------------------------------------
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val xxxxl = 56.dp

    // ---- Radii ------------------------------------------------------------
    val radiusSm = RoundedCornerShape(4.dp)
    val radiusMd = RoundedCornerShape(12.dp)
    val radiusLg = RoundedCornerShape(20.dp)
    val radiusXl = RoundedCornerShape(28.dp)

    // ---- Motion -----------------------------------------------------------
    /** Micro-feedback: button press scale, chip select, toggle flip. */
    const val motionFast = 120

    /** Standard transitions: tab change, card expand, sheet half-open. */
    const val motionBase = 220

    /** Narrative moments: model-loaded, route change, audit-log open. */
    const val motionSlow = 360

    val easingStandard = FastOutSlowInEasing
    val easingDecelerate = LinearOutSlowInEasing
    val easingEmphasize = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}
