package de.puzzlewerk.app.ui.theme

import androidx.compose.material3.Typography

/**
 * Typografie der App: bewusst die Material-3-Defaults (System-Roboto) —
 * keine Font-Assets in V1 (Regel C8: keine neuen Dependencies ohne ADR).
 * sp-Skalierung bleibt damit systemtreu (§13.6).
 */
internal val puzzlewerkTypography = Typography()
