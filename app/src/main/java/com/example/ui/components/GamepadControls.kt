package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ClusterTransform
import com.example.model.ControlClusterId
import com.example.model.PsButton
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DeckBorderIdle
import com.example.ui.theme.DeepSpaceNavy
import com.example.ui.theme.NeonAzure
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate
import com.example.ui.theme.TurboAmber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Wraps any controller cluster to support user drag-and-drop repositioning and scaling
 * in the Custom Layout Editor.
 */
@Composable
fun EditableClusterContainer(
    clusterId: ControlClusterId,
    transform: ClusterTransform,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: (ControlClusterId) -> Unit,
    onDragDeltaDp: (ControlClusterId, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val borderMod = if (isEditMode) {
        Modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) TurboAmber else NeonCyan.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = if (isSelected) TurboAmber.copy(alpha = 0.08f) else NeonCyan.copy(alpha = 0.04f),
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(clusterId, isEditMode) {
                detectDragGestures(
                    onDragStart = { onSelect(clusterId) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dxDp = with(density) { dragAmount.x.toDp().value }
                        val dyDp = with(density) { dragAmount.y.toDp().value }
                        onDragDeltaDp(clusterId, dxDp, dyDp)
                    }
                )
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = with(density) { transform.offsetX.dp.roundToPx() },
                    y = with(density) { transform.offsetY.dp.roundToPx() }
                )
            }
            .scale(transform.scale)
            .then(borderMod)
            .padding(if (isEditMode) 4.dp else 0.dp)
    ) {
        content()
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(
                        color = if (isSelected) TurboAmber else NeonCyan,
                        shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "${(transform.scale * 100).roundToInt()}%",
                    color = ObsidianBlack,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * High-precision Multi-Touch Analog Thumbstick with L3/R3 Stick Click button,
 * concentric reticle rings, and glowing displacement vector.
 */
@Composable
fun NeonAnalogThumbstick(
    label: String,
    stickClickButton: PsButton,
    stickX: Float,
    stickY: Float,
    isStickClickPressed: Boolean,
    isEditMode: Boolean,
    onStickMove: (Float, Float) -> Unit,
    onButtonPress: (PsButton) -> Unit,
    onButtonRelease: (PsButton) -> Unit,
    diameter: Dp = 128.dp,
    modifier: Modifier = Modifier
) {
    val animatedX by animateFloatAsState(
        targetValue = stickX,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 900f),
        label = "stickX"
    )
    val animatedY by animateFloatAsState(
        targetValue = stickY,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 900f),
        label = "stickY"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag("analog_stick_${label.lowercase()}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                .then(
                    if (!isEditMode) {
                        Modifier.pointerInput(label) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val maxRadius = (size.width / 2f) * 0.78f

                                fun updateFromOffset(pos: Offset) {
                                    val dx = pos.x - center.x
                                    val dy = pos.y - center.y
                                    val dist = hypot(dx, dy)
                                    if (dist <= maxRadius * 0.08f) {
                                        onStickMove(0f, 0f)
                                    } else {
                                        val clampedDist = dist.coerceAtMost(maxRadius)
                                        val angle = atan2(dy, dx)
                                        val normX = (cos(angle) * (clampedDist / maxRadius)).coerceIn(-1f, 1f)
                                        val normY = (sin(angle) * (clampedDist / maxRadius)).coerceIn(-1f, 1f)
                                        onStickMove(normX, normY)
                                    }
                                }

                                updateFromOffset(down.position)
                                do {
                                    val event = awaitPointerEvent()
                                    val activeChange = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (activeChange != null && activeChange.pressed) {
                                        activeChange.consume()
                                        updateFromOffset(activeChange.position)
                                    }
                                } while (event.changes.any { it.pressed })

                                onStickMove(0f, 0f)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f - 4.dp.toPx()
                val maxTravel = outerRadius * 0.62f
                val displacementMag = hypot(animatedX, animatedY).coerceIn(0f, 1f)

                // Outer obsidian well
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(DeepSpaceNavy, ObsidianBlack),
                        center = center,
                        radius = outerRadius
                    ),
                    radius = outerRadius,
                    center = center
                )

                // Glowing outer ring
                drawCircle(
                    color = if (displacementMag > 0.08f) {
                        NeonCyan.copy(alpha = 0.45f + 0.45f * displacementMag)
                    } else {
                        DeckBorderIdle
                    },
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 2.2.dp.toPx())
                )

                // Inner reticle ring
                drawCircle(
                    color = NeonAzure.copy(alpha = 0.22f),
                    radius = outerRadius * 0.65f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Crosshair axis ticks
                val tickLen = 8.dp.toPx()
                drawLine(
                    color = NeonCyan.copy(alpha = 0.3f),
                    start = Offset(center.x, center.y - outerRadius + 4f),
                    end = Offset(center.x, center.y - outerRadius + 4f + tickLen),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = NeonCyan.copy(alpha = 0.3f),
                    start = Offset(center.x, center.y + outerRadius - 4f - tickLen),
                    end = Offset(center.x, center.y + outerRadius - 4f),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = NeonCyan.copy(alpha = 0.3f),
                    start = Offset(center.x - outerRadius + 4f, center.y),
                    end = Offset(center.x - outerRadius + 4f + tickLen, center.y),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = NeonCyan.copy(alpha = 0.3f),
                    start = Offset(center.x + outerRadius - 4f - tickLen, center.y),
                    end = Offset(center.x + outerRadius - 4f, center.y),
                    strokeWidth = 1.5.dp.toPx()
                )

                val knobCenter = Offset(
                    x = center.x + animatedX * maxTravel,
                    y = center.y + animatedY * maxTravel
                )

                // Vector line from center to knob
                if (displacementMag > 0.05f) {
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.55f),
                        start = center,
                        end = knobCenter,
                        strokeWidth = 2.5.dp.toPx()
                    )
                }

                // Thumbstick Cap Halo
                val knobRadius = outerRadius * 0.42f
                if (displacementMag > 0.05f || isStickClickPressed) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan.copy(alpha = 0.45f), Color.Transparent),
                            center = knobCenter,
                            radius = knobRadius * 1.6f
                        ),
                        radius = knobRadius * 1.6f,
                        center = knobCenter
                    )
                }

                // Thumbstick Cap Body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CarbonElevated, CarbonSurface, ObsidianBlack),
                        center = knobCenter,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = knobCenter
                )

                // Thumbstick Cap Rim
                drawCircle(
                    color = if (isStickClickPressed) TurboAmber else NeonCyan,
                    radius = knobRadius,
                    center = knobCenter,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Inner textured grip ring
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.35f),
                    radius = knobRadius * 0.55f,
                    center = knobCenter,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Dedicated L3 / R3 Stick Click Pill
        MomentaryNeonButton(
            button = stickClickButton,
            isPressed = isStickClickPressed,
            isTurboAssigned = false,
            isEditMode = isEditMode,
            onPress = { onButtonPress(stickClickButton) },
            onRelease = { onButtonRelease(stickClickButton) },
            modifier = Modifier
                .width(64.dp)
                .height(26.dp)
                .testTag("button_${stickClickButton.id.lowercase()}")
        ) {
            Text(
                text = stickClickButton.symbol,
                color = if (isStickClickPressed) ObsidianBlack else NeonCyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * PlayStation Action Cluster (Triangle △, Circle ◯, Cross ✕, Square ▢) arranged in a diamond.
 */
@Composable
fun PlayStationActionCluster(
    pressedButtons: Set<PsButton>,
    turboButtons: Set<PsButton>,
    isEditMode: Boolean,
    onButtonPress: (PsButton) -> Unit,
    onButtonRelease: (PsButton) -> Unit,
    clusterSize: Dp = 156.dp,
    buttonSize: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(clusterSize)
            .testTag("ps_action_cluster")
    ) {
        // Center subtle decorative X-axis reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = DeckBorderIdle.copy(alpha = 0.5f),
                radius = size.minDimension * 0.34f,
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Top: TRIANGLE (△)
        PsGlyphCircleButton(
            button = PsButton.TRIANGLE,
            isPressed = PsButton.TRIANGLE in pressedButtons,
            isTurbo = PsButton.TRIANGLE in turboButtons,
            isEditMode = isEditMode,
            size = buttonSize,
            onPress = { onButtonPress(PsButton.TRIANGLE) },
            onRelease = { onButtonRelease(PsButton.TRIANGLE) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Bottom: CROSS (✕)
        PsGlyphCircleButton(
            button = PsButton.CROSS,
            isPressed = PsButton.CROSS in pressedButtons,
            isTurbo = PsButton.CROSS in turboButtons,
            isEditMode = isEditMode,
            size = buttonSize,
            onPress = { onButtonPress(PsButton.CROSS) },
            onRelease = { onButtonRelease(PsButton.CROSS) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Left: SQUARE (▢)
        PsGlyphCircleButton(
            button = PsButton.SQUARE,
            isPressed = PsButton.SQUARE in pressedButtons,
            isTurbo = PsButton.SQUARE in turboButtons,
            isEditMode = isEditMode,
            size = buttonSize,
            onPress = { onButtonPress(PsButton.SQUARE) },
            onRelease = { onButtonRelease(PsButton.SQUARE) },
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Right: CIRCLE (◯)
        PsGlyphCircleButton(
            button = PsButton.CIRCLE,
            isPressed = PsButton.CIRCLE in pressedButtons,
            isTurbo = PsButton.CIRCLE in turboButtons,
            isEditMode = isEditMode,
            size = buttonSize,
            onPress = { onButtonPress(PsButton.CIRCLE) },
            onRelease = { onButtonRelease(PsButton.CIRCLE) },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun PsGlyphCircleButton(
    button: PsButton,
    isPressed: Boolean,
    isTurbo: Boolean,
    isEditMode: Boolean,
    size: Dp,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1000f),
        label = "btnScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(scale)
            .testTag("button_${button.id.lowercase()}")
            .then(
                if (!isEditMode) {
                    Modifier.pointerInput(button) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onPress()
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            onRelease()
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f - 2.dp.toPx()

            if (isPressed) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(button.accentColor.copy(alpha = 0.55f), Color.Transparent),
                        center = c,
                        radius = r * 1.35f
                    ),
                    radius = r * 1.35f,
                    center = c
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isPressed) {
                        listOf(button.accentColor.copy(alpha = 0.4f), CarbonElevated)
                    } else {
                        listOf(CarbonElevated, ObsidianBlack)
                    },
                    center = c,
                    radius = r
                ),
                radius = r,
                center = c
            )

            drawCircle(
                color = if (isPressed) button.accentColor else button.accentColor.copy(alpha = 0.65f),
                radius = r,
                center = c,
                style = Stroke(width = if (isPressed) 2.6.dp.toPx() else 1.6.dp.toPx())
            )

            // Draw authentic geometric PlayStation vector glyphs
            val glyphRadius = r * 0.42f
            val strokeW = 2.4.dp.toPx()
            val glyphColor = if (isPressed) Color.White else button.accentColor

            when (button) {
                PsButton.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(c.x, c.y - glyphRadius)
                        lineTo(c.x + glyphRadius * 0.92f, c.y + glyphRadius * 0.72f)
                        lineTo(c.x - glyphRadius * 0.92f, c.y + glyphRadius * 0.72f)
                        close()
                    }
                    drawPath(path = path, color = glyphColor, style = Stroke(width = strokeW))
                }
                PsButton.CIRCLE -> {
                    drawCircle(
                        color = glyphColor,
                        radius = glyphRadius * 0.88f,
                        center = c,
                        style = Stroke(width = strokeW)
                    )
                }
                PsButton.CROSS -> {
                    val d = glyphRadius * 0.76f
                    drawLine(
                        color = glyphColor,
                        start = Offset(c.x - d, c.y - d),
                        end = Offset(c.x + d, c.y + d),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = glyphColor,
                        start = Offset(c.x + d, c.y - d),
                        end = Offset(c.x - d, c.y + d),
                        strokeWidth = strokeW
                    )
                }
                PsButton.SQUARE -> {
                    val d = glyphRadius * 0.78f
                    drawRoundRect(
                        color = glyphColor,
                        topLeft = Offset(c.x - d, c.y - d),
                        size = Size(d * 2f, d * 2f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(width = strokeW)
                    )
                }
                else -> {}
            }
        }

        if (isTurbo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(TurboAmber),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚡",
                    fontSize = 7.sp,
                    color = ObsidianBlack,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 8-Way Responsive PlayStation Directional Pad (D-Pad) supporting single directions
 * and smooth diagonal thumb slides.
 */
@Composable
fun PlayStationDpad(
    pressedButtons: Set<PsButton>,
    isEditMode: Boolean,
    onDpadChange: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit,
    dpadSize: Dp = 148.dp,
    modifier: Modifier = Modifier
) {
    val upPressed = PsButton.DPAD_UP in pressedButtons
    val downPressed = PsButton.DPAD_DOWN in pressedButtons
    val leftPressed = PsButton.DPAD_LEFT in pressedButtons
    val rightPressed = PsButton.DPAD_RIGHT in pressedButtons

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(dpadSize)
            .testTag("ps_dpad_cluster")
            .then(
                if (!isEditMode) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val deadzone = minOf(size.width, size.height) * 0.14f

                            fun resolveDirections(pos: Offset) {
                                val dx = pos.x - center.x
                                val dy = pos.y - center.y
                                val dist = hypot(dx, dy)
                                if (dist < deadzone) {
                                    onDpadChange(false, false, false, false)
                                    return
                                }
                                val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                                // 8-way sector mapping (-180..+180)
                                val right = angleDeg in -60.0..60.0
                                val downDir = angleDeg in 30.0..150.0
                                val upDir = angleDeg in -150.0..-30.0
                                val left = angleDeg >= 120.0 || angleDeg <= -120.0
                                onDpadChange(upDir, downDir, left, right)
                            }

                            resolveDirections(down.position)
                            do {
                                val event = awaitPointerEvent()
                                val active = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (active != null && active.pressed) {
                                    active.consume()
                                    resolveDirections(active.position)
                                }
                            } while (event.changes.any { it.pressed })

                            onDpadChange(false, false, false, false)
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val armWidth = size.minDimension * 0.30f
            val armLength = size.minDimension * 0.46f

            // Outer ambient ring
            drawCircle(
                color = DeckBorderIdle.copy(alpha = 0.6f),
                radius = size.minDimension / 2f - 2.dp.toPx(),
                center = c,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Helper to draw each directional arm
            fun drawArm(topLeft: Offset, boxSize: Size, active: Boolean) {
                drawRoundRect(
                    color = if (active) NeonCyan.copy(alpha = 0.35f) else CarbonElevated,
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                drawRoundRect(
                    color = if (active) NeonCyan else NeonCyan.copy(alpha = 0.45f),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = if (active) 2.2.dp.toPx() else 1.2.dp.toPx())
                )
            }

            // Up Arm
            drawArm(
                topLeft = Offset(c.x - armWidth / 2f, c.y - armLength),
                boxSize = Size(armWidth, armLength * 0.72f),
                active = upPressed
            )
            // Down Arm
            drawArm(
                topLeft = Offset(c.x - armWidth / 2f, c.y + armLength * 0.28f),
                boxSize = Size(armWidth, armLength * 0.72f),
                active = downPressed
            )
            // Left Arm
            drawArm(
                topLeft = Offset(c.x - armLength, c.y - armWidth / 2f),
                boxSize = Size(armLength * 0.72f, armWidth),
                active = leftPressed
            )
            // Right Arm
            drawArm(
                topLeft = Offset(c.x + armLength * 0.28f, c.y - armWidth / 2f),
                boxSize = Size(armLength * 0.72f, armWidth),
                active = rightPressed
            )

            // Center pivot circle
            drawCircle(
                color = ObsidianBlack,
                radius = armWidth * 0.36f,
                center = c
            )
        }

        // Directional Arrow Labels
        Text(
            text = "▲",
            color = if (upPressed) TextPrimaryWhite else NeonCyan,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        )
        Text(
            text = "▼",
            color = if (downPressed) TextPrimaryWhite else NeonCyan,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
        Text(
            text = "◀",
            color = if (leftPressed) TextPrimaryWhite else NeonCyan,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
        )
        Text(
            text = "▶",
            color = if (rightPressed) TextPrimaryWhite else NeonCyan,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )
    }
}

/**
 * Shoulder Bumper (L1/R1) + Pressure-Sensitive Analog Trigger (L2/R2) Module.
 */
@Composable
fun ShoulderTriggerCluster(
    isLeft: Boolean,
    bumperButton: PsButton,
    triggerButton: PsButton,
    isBumperPressed: Boolean,
    isTriggerPressed: Boolean,
    triggerAnalogValue: Float,
    isBumperTurbo: Boolean,
    isTriggerTurbo: Boolean,
    isEditMode: Boolean,
    onButtonPress: (PsButton) -> Unit,
    onButtonRelease: (PsButton) -> Unit,
    onAnalogTriggerChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.testTag(if (isLeft) "left_shoulder_cluster" else "right_shoulder_cluster")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Analog L2 / R2 Trigger Button + Slide Bar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(96.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarbonSurface)
                    .border(
                        width = if (isTriggerPressed) 2.dp else 1.2.dp,
                        color = if (isTriggerPressed) NeonCyan else NeonAzure.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("button_${triggerButton.id.lowercase()}")
                    .then(
                        if (!isEditMode) {
                            Modifier.pointerInput(triggerButton) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    fun computePressure(xPos: Float): Float {
                                        val ratio = (xPos / size.width.toFloat()).coerceIn(0.15f, 1f)
                                        return if (ratio > 0.85f) 1f else ratio
                                    }
                                    onAnalogTriggerChange(1f)
                                    do {
                                        val ev = awaitPointerEvent()
                                        val active = ev.changes.firstOrNull { it.id == down.id }
                                            ?: ev.changes.firstOrNull()
                                        if (active != null && active.pressed) {
                                            active.consume()
                                            onAnalogTriggerChange(computePressure(active.position.x))
                                        }
                                    } while (ev.changes.any { it.pressed })
                                    onAnalogTriggerChange(0f)
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Fill progress bar showing analog trigger pull
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(triggerAnalogValue.coerceIn(0f, 1f))
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NeonAzure.copy(alpha = 0.55f), NeonCyan.copy(alpha = 0.75f))
                                )
                            )
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = triggerButton.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.Bold
                    )
                    if (triggerAnalogValue > 0.05f) {
                        Text(
                            text = "${(triggerAnalogValue * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    } else if (isTriggerTurbo) {
                        Text(text = "⚡", fontSize = 10.sp, color = TurboAmber)
                    }
                }
            }

            // L1 / R1 Tactile Bumper Button
            MomentaryNeonButton(
                button = bumperButton,
                isPressed = isBumperPressed,
                isTurboAssigned = isBumperTurbo,
                isEditMode = isEditMode,
                onPress = { onButtonPress(bumperButton) },
                onRelease = { onButtonRelease(bumperButton) },
                modifier = Modifier
                    .width(86.dp)
                    .height(42.dp)
                    .testTag("button_${bumperButton.id.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = bumperButton.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isBumperPressed) ObsidianBlack else NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    if (isBumperTurbo) {
                        Text(
                            text = "⚡",
                            fontSize = 10.sp,
                            color = if (isBumperPressed) ObsidianBlack else TurboAmber
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable low-latency momentary touch button that triggers immediately on finger down
 * and releases on finger lift.
 */
@Composable
fun MomentaryNeonButton(
    button: PsButton,
    isPressed: Boolean,
    isTurboAssigned: Boolean,
    isEditMode: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPressed) accentColor else CarbonElevated
            )
            .border(
                width = if (isPressed) 2.dp else 1.2.dp,
                color = if (isPressed) TextPrimaryWhite else accentColor.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (!isEditMode) {
                    Modifier.pointerInput(button) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onPress()
                            do {
                                val ev = awaitPointerEvent()
                            } while (ev.changes.any { it.pressed })
                            onRelease()
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

/**
 * Center Console Touchpad, Share, Options, and PS Home Deck.
 */
@Composable
fun CenterConsoleDeck(
    pressedButtons: Set<PsButton>,
    leftX: Float,
    leftY: Float,
    rightX: Float,
    rightY: Float,
    gyroEnabled: Boolean,
    pitchDeg: Float,
    rollDeg: Float,
    isEditMode: Boolean,
    onButtonPress: (PsButton) -> Unit,
    onButtonRelease: (PsButton) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag("center_console_deck")
    ) {
        // Share + Interactive Touchpad Bar + Options
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MomentaryNeonButton(
                button = PsButton.SHARE,
                isPressed = PsButton.SHARE in pressedButtons,
                isTurboAssigned = false,
                isEditMode = isEditMode,
                onPress = { onButtonPress(PsButton.SHARE) },
                onRelease = { onButtonRelease(PsButton.SHARE) },
                modifier = Modifier
                    .width(58.dp)
                    .height(34.dp)
                    .testTag("button_share")
            ) {
                Text(
                    text = "SHARE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (PsButton.SHARE in pressedButtons) ObsidianBlack else TextPrimaryWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            // DualSense Styled Capacitive Touchpad with Live Telemetry Readout
            val tpadPressed = PsButton.TOUCHPAD in pressedButtons
            MomentaryNeonButton(
                button = PsButton.TOUCHPAD,
                isPressed = tpadPressed,
                isTurboAssigned = false,
                isEditMode = isEditMode,
                onPress = { onButtonPress(PsButton.TOUCHPAD) },
                onRelease = { onButtonRelease(PsButton.TOUCHPAD) },
                accentColor = NeonAzure,
                modifier = Modifier
                    .width(142.dp)
                    .height(54.dp)
                    .testTag("button_touchpad")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = if (tpadPressed) "TOUCHPAD ACTIVE" else "DUALSENSE TOUCHPAD",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tpadPressed) ObsidianBlack else NeonCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (gyroEnabled) {
                            "GYRO P:${"%.0f".format(pitchDeg)}° R:${"%.0f".format(rollDeg)}°"
                        } else {
                            "L:${"%.1f".format(leftX)},${"%.1f".format(leftY)} R:${"%.1f".format(rightX)},${"%.1f".format(rightY)}"
                        },
                        fontSize = 9.sp,
                        color = if (tpadPressed) ObsidianBlack else TextSecondarySlate
                    )
                }
            }

            MomentaryNeonButton(
                button = PsButton.OPTIONS,
                isPressed = PsButton.OPTIONS in pressedButtons,
                isTurboAssigned = false,
                isEditMode = isEditMode,
                onPress = { onButtonPress(PsButton.OPTIONS) },
                onRelease = { onButtonRelease(PsButton.OPTIONS) },
                modifier = Modifier
                    .width(58.dp)
                    .height(34.dp)
                    .testTag("button_options")
            ) {
                Text(
                    text = "OPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (PsButton.OPTIONS in pressedButtons) ObsidianBlack else TextPrimaryWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // PS Home Emblem Button
        val psPressed = PsButton.PS_HOME in pressedButtons
        MomentaryNeonButton(
            button = PsButton.PS_HOME,
            isPressed = psPressed,
            isTurboAssigned = false,
            isEditMode = isEditMode,
            accentColor = TurboAmber,
            onPress = { onButtonPress(PsButton.PS_HOME) },
            onRelease = { onButtonRelease(PsButton.PS_HOME) },
            modifier = Modifier
                .width(68.dp)
                .height(32.dp)
                .testTag("button_ps_home")
        ) {
            Text(
                text = "PS HOME",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (psPressed) ObsidianBlack else TurboAmber
            )
        }
    }
}
