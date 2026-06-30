@file:OptIn(ExperimentalFoundationApi::class)

package com.justgba.input

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min

object GbaButtons {
    const val A = 8
    const val B = 0
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val L = 10
    const val R = 11
}

@Composable
fun VirtualGamepad(
    modifier: Modifier = Modifier,
    onButtonDown: (Int) -> Unit = {},
    onButtonUp: (Int) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    if (isLandscape) {
        LandscapeGamepad(
            modifier = modifier,
            onButtonDown = onButtonDown,
            onButtonUp = onButtonUp,
        )
    } else {
        PortraitGamepad(
            modifier = modifier,
            onButtonDown = onButtonDown,
            onButtonUp = onButtonUp,
        )
    }
}

@Composable
private fun LandscapeGamepad(
    modifier: Modifier = Modifier,
    onButtonDown: (Int) -> Unit = {},
    onButtonUp: (Int) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DPad(
            onDown = { onButtonDown(GbaButtons.DOWN) },
            onUp = { onButtonDown(GbaButtons.UP) },
            onLeft = { onButtonDown(GbaButtons.LEFT) },
            onRight = { onButtonDown(GbaButtons.RIGHT) },
            onRelease = { id -> onButtonUp(id) }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameButton("Select", GbaButtons.SELECT, { onButtonDown(GbaButtons.SELECT) }, { onButtonUp(GbaButtons.SELECT) }, Modifier.size(52.dp))
            GameButton("Start", GbaButtons.START, { onButtonDown(GbaButtons.START) }, { onButtonUp(GbaButtons.START) }, Modifier.size(52.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShoulderButton("L", GbaButtons.L, onButtonDown, onButtonUp)
            ShoulderButton("R", GbaButtons.R, onButtonDown, onButtonUp)
        }

        ActionButtons(
            onA = { onButtonDown(GbaButtons.A) },
            onB = { onButtonDown(GbaButtons.B) },
            onRelease = { id -> onButtonUp(id) }
        )
    }
}

@Composable
private fun PortraitGamepad(
    modifier: Modifier = Modifier,
    onButtonDown: (Int) -> Unit = {},
    onButtonUp: (Int) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DPad(
                onDown = { onButtonDown(GbaButtons.DOWN) },
                onUp = { onButtonDown(GbaButtons.UP) },
                onLeft = { onButtonDown(GbaButtons.LEFT) },
                onRight = { onButtonDown(GbaButtons.RIGHT) },
                onRelease = { id -> onButtonUp(id) }
            )
            ActionButtons(
                onA = { onButtonDown(GbaButtons.A) },
                onB = { onButtonDown(GbaButtons.B) },
                onRelease = { id -> onButtonUp(id) }
            )
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShoulderButton("L", GbaButtons.L, onButtonDown, onButtonUp)
            Spacer(Modifier.width(12.dp))
            GameButton("Select", GbaButtons.SELECT, { onButtonDown(GbaButtons.SELECT) }, { onButtonUp(GbaButtons.SELECT) }, Modifier.size(52.dp))
            Spacer(Modifier.width(8.dp))
            GameButton("Start", GbaButtons.START, { onButtonDown(GbaButtons.START) }, { onButtonUp(GbaButtons.START) }, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            ShoulderButton("R", GbaButtons.R, onButtonDown, onButtonUp)
        }
    }
}

@Composable
private fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onRelease: (Int) -> Unit,
) {
    val dpSize = 150.dp
    val dpArmWidth = 46.dp
    val dpCornerRadius = 6.dp

    var pressedDirection by remember { mutableStateOf(-1) }

    Canvas(
        modifier = Modifier
            .size(dpSize)
            .pointerInput(Unit) {
                val wPx = this.size.width.toFloat()
                val hPx = this.size.height.toFloat()
                val cxPx = wPx / 2f
                val cyPx = hPx / 2f
                val awPx = dpArmWidth.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    var currentDir = hitTestDirection(down.position.x, down.position.y, cxPx, cyPx, awPx)
                    if (currentDir != -1) {
                        pressedDirection = currentDir
                        when (currentDir) {
                            GbaButtons.UP -> onUp()
                            GbaButtons.DOWN -> onDown()
                            GbaButtons.LEFT -> onLeft()
                            GbaButtons.RIGHT -> onRight()
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (currentDir != -1) {
                                pressedDirection = -1
                                onRelease(currentDir)
                            }
                            break
                        }
                        val newDir = hitTestDirection(change.position.x, change.position.y, cxPx, cyPx, awPx)
                        if (newDir != currentDir) {
                            if (currentDir != -1) onRelease(currentDir)
                            currentDir = newDir
                            if (newDir != -1) {
                                pressedDirection = newDir
                                when (newDir) {
                                    GbaButtons.UP -> onUp()
                                    GbaButtons.DOWN -> onDown()
                                    GbaButtons.LEFT -> onLeft()
                                    GbaButtons.RIGHT -> onRight()
                                }
                            } else {
                                pressedDirection = -1
                            }
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val aw = min(w, h) * (dpArmWidth.value / dpSize.value).toFloat()
        val cr = min(
            min(w, h) * (dpCornerRadius.value / dpSize.value).toFloat(),
            aw / 4f
        )

        val bgColor = Color(0x77555555)
        val pressColor = Color(0xFF4CAF50)

        val centerRect = Rect(cx - aw / 2f, cy - aw / 2f, cx + aw / 2f, cy + aw / 2f)

        listOf(GbaButtons.UP, GbaButtons.DOWN, GbaButtons.LEFT, GbaButtons.RIGHT).forEach { dir ->
            drawPath(armPath(dir, cx, cy, aw, w, h, cr), bgColor)
        }
        drawPath(Path().apply { addRect(centerRect) }, bgColor)

        if (pressedDirection != -1) {
            drawPath(armPath(pressedDirection, cx, cy, aw, w, h, cr), pressColor)
        }

        val arrowSize = aw * 0.35f
        drawArrow(GbaButtons.UP, cx, cy, aw, arrowSize, Color.White)
        drawArrow(GbaButtons.DOWN, cx, cy, aw, arrowSize, Color.White)
        drawArrow(GbaButtons.LEFT, cx, cy, aw, arrowSize, Color.White)
        drawArrow(GbaButtons.RIGHT, cx, cy, aw, arrowSize, Color.White)
    }
}

private fun hitTestDirection(
    px: Float, py: Float,
    cx: Float, cy: Float,
    aw: Float,
): Int {
    val dx = px - cx
    val dy = py - cy
    val halfAw = aw / 2f

    val inVertArm = abs(dx) < halfAw
    val inHorizArm = abs(dy) < halfAw

    if (!inVertArm && !inHorizArm) return -1

    if (inVertArm && inHorizArm) {
        return if (abs(dx) > abs(dy)) {
            if (dx < 0) GbaButtons.LEFT else GbaButtons.RIGHT
        } else {
            if (dy < 0) GbaButtons.UP else GbaButtons.DOWN
        }
    }

    return if (inVertArm) {
        if (dy < 0) GbaButtons.UP else GbaButtons.DOWN
    } else {
        if (dx < 0) GbaButtons.LEFT else GbaButtons.RIGHT
    }
}

private fun armPath(
    direction: Int,
    cx: Float, cy: Float,
    aw: Float, w: Float, h: Float,
    cr: Float,
): Path = Path().apply {
    val z = CornerRadius.Zero
    when (direction) {
        GbaButtons.UP -> addRoundRect(
            RoundRect(
                rect = Rect(cx - aw / 2f, 0f, cx + aw / 2f, cy - aw / 2f),
                topLeft = CornerRadius(cr),
                topRight = CornerRadius(cr),
                bottomRight = z,
                bottomLeft = z,
            )
        )
        GbaButtons.DOWN -> addRoundRect(
            RoundRect(
                rect = Rect(cx - aw / 2f, cy + aw / 2f, cx + aw / 2f, h),
                topLeft = z,
                topRight = z,
                bottomRight = CornerRadius(cr),
                bottomLeft = CornerRadius(cr),
            )
        )
        GbaButtons.LEFT -> addRoundRect(
            RoundRect(
                rect = Rect(0f, cy - aw / 2f, cx - aw / 2f, cy + aw / 2f),
                topLeft = CornerRadius(cr),
                topRight = z,
                bottomRight = z,
                bottomLeft = CornerRadius(cr),
            )
        )
        GbaButtons.RIGHT -> addRoundRect(
            RoundRect(
                rect = Rect(cx + aw / 2f, cy - aw / 2f, w, cy + aw / 2f),
                topLeft = z,
                topRight = CornerRadius(cr),
                bottomRight = CornerRadius(cr),
                bottomLeft = z,
            )
        )
    }
}

private fun DrawScope.drawArrow(
    direction: Int,
    cx: Float, cy: Float,
    aw: Float, s: Float,
    color: Color,
) {
    val sw = size.width
    val sh = size.height
    val path = Path().apply {
        when (direction) {
            GbaButtons.UP -> {
                val y = (cy - aw / 2f) / 2f
                moveTo(cx, y - s)
                lineTo(cx - s * 0.8f, y)
                lineTo(cx + s * 0.8f, y)
                close()
            }
            GbaButtons.DOWN -> {
                val y = (sh + cy + aw / 2f) / 2f
                moveTo(cx, y + s)
                lineTo(cx - s * 0.8f, y)
                lineTo(cx + s * 0.8f, y)
                close()
            }
            GbaButtons.LEFT -> {
                val x = (cx - aw / 2f) / 2f
                moveTo(x - s, cy)
                lineTo(x, cy - s * 0.8f)
                lineTo(x, cy + s * 0.8f)
                close()
            }
            GbaButtons.RIGHT -> {
                val x = (sw + cx + aw / 2f) / 2f
                moveTo(x + s, cy)
                lineTo(x, cy - s * 0.8f)
                lineTo(x, cy + s * 0.8f)
                close()
            }
        }
    }
    drawPath(path, color)
}

@Composable
private fun ActionButtons(
    onA: () -> Unit,
    onB: () -> Unit,
    onRelease: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameButton("B", GbaButtons.B, { onB() }, { onRelease(GbaButtons.B) }, Modifier.size(64.dp))
        GameButton("A", GbaButtons.A, { onA() }, { onRelease(GbaButtons.A) }, Modifier.size(64.dp))
    }
}

@Composable
private fun GameButton(
    text: String,
    id: Int,
    onDown: () -> Unit,
    onUp: (Int) -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    bg: Color = Color(0x77555555),
) {
    var isPressed by remember { mutableStateOf(false) }

    val displayColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF4CAF50) else bg,
        animationSpec = tween(durationMillis = 120),
        label = "btnColor",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(displayColor, CircleShape)
            .border(1.dp, Color(0x44FFFFFF), CircleShape)
            .pointerInput(id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onDown()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                        }
                    } finally {
                        isPressed = false
                        onUp(id)
                    }
                }
            }
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShoulderButton(
    text: String,
    id: Int,
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }

    val displayColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF4CAF50) else Color(0x77555555),
        animationSpec = tween(durationMillis = 120),
        label = "shoulderColor",
    )

    val shape = RoundedCornerShape(8.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .background(displayColor, shape)
            .border(1.dp, Color(0x44FFFFFF), shape)
            .pointerInput(id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onDown(id)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                        }
                    } finally {
                        isPressed = false
                        onUp(id)
                    }
                }
            }
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
