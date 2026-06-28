package com.justgba.input

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            GameButton("Select", GbaButtons.SELECT, { onButtonDown(GbaButtons.SELECT) }, { onButtonUp(GbaButtons.SELECT) })
            GameButton("Start", GbaButtons.START, { onButtonDown(GbaButtons.START) }, { onButtonUp(GbaButtons.START) })
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
private fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onRelease: (Int) -> Unit,
) {
    val size = 120.dp
    val btnSize = 36.dp
    val gap = 2.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.size(size)
    ) {
        GameButtonT("▲", GbaButtons.UP, { onUp() }, { onRelease(GbaButtons.UP) },
            Modifier.size(btnSize))
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameButtonT("◄", GbaButtons.LEFT, { onLeft() }, { onRelease(GbaButtons.LEFT) },
                Modifier.size(btnSize))
            Box(modifier = Modifier.size(btnSize))
            GameButtonT("►", GbaButtons.RIGHT, { onRight() }, { onRelease(GbaButtons.RIGHT) },
                Modifier.size(btnSize))
        }
        GameButtonT("▼", GbaButtons.DOWN, { onDown() }, { onRelease(GbaButtons.DOWN) },
            Modifier.size(btnSize))
    }
}

@Composable
private fun GameButtonT(
    text: String,
    id: Int,
    onDown: () -> Unit,
    onUp: (Int) -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    bg: Color = Color(0xFF555555),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .pointerInput(id) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        awaitRelease()
                        onUp(id)
                    }
                )
            }
    ) {
        Text(
            text, color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
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
        GameButton("B", GbaButtons.B, { onB() }, { onRelease(GbaButtons.B) },
            Modifier.size(56.dp), Color(0xFFE53935))
        GameButton("A", GbaButtons.A, { onA() }, { onRelease(GbaButtons.A) },
            Modifier.size(56.dp), Color(0xFF43A047))
    }
}

@Composable
private fun GameButton(
    text: String,
    id: Int,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    bg: Color = Color(0xFF555555),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .pointerInput(id) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        awaitRelease()
                        onUp()
                    }
                )
            }
    ) {
        Text(
            text, color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF777777))
            .pointerInput(id) {
                detectTapGestures(
                    onPress = {
                        onDown(id)
                        awaitRelease()
                        onUp(id)
                    }
                )
            }
    ) {
        Text(
            text, color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
