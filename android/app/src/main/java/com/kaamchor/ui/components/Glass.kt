package com.kaamchor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

// ── Haze helpers ─────────────────────────────────────────────────────────────
// Mark a scrolling / hero container as the blur *source*.
fun Modifier.asHazeSource(state: HazeState): Modifier = this.hazeSource(state)

@Composable
fun rememberGlassState(): HazeState = rememberHazeState()

/**
 * Subtle Liquid-Glass surface: translucent Material container + faint hairline
 * border + backdrop blur (real refraction on API 33+, translucency below).
 */
@Composable
fun GlassSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    padding: PaddingValues = PaddingValues(0.dp),
    containerAlpha: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceContainer
    val border = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(hazeState, HazeMaterials.thin(container))
            .background(container.copy(alpha = containerAlpha))
            .border(1.dp, border.copy(alpha = 0.5f), shape)
            .padding(padding),
        content = content,
    )
}

/** Hairline gradient used for the glass button/sheet edge highlight. */@Composable
fun glassEdgeBrush(): Brush {
    val hi = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val lo = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
    return Brush.verticalGradient(listOf(hi, lo))
}

@Composable
fun glassShape(radius: Dp = 24.dp): Shape = RoundedCornerShape(radius)

/**
 * Translucent card used where there is no blur source behind it (e.g. list
 * items). Keeps the frosted look via a tinted container + hairline border
 * without needing a HazeState.
 */
@Composable
fun TranslucentCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    padding: PaddingValues = PaddingValues(0.dp),
    containerAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier = modifier
            .clip(shape)
            .background(container.copy(alpha = containerAlpha))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .padding(padding),
        content = content,
    )
}
