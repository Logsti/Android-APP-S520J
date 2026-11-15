package com.s520j.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/** 缩放范围 0.5f–4f，超出边界自动回弹；单图查看与多图分页共用。 */
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 4f

/**
 * 全屏图片查看器：点击缩略图后弹出，支持双指缩放、单指拖动、点击空白关闭。
 */
@Composable
fun ImageViewerDialog(
    model: Any?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        ) {
            ZoomableImage(model = model, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * 可复用的缩放图片：双指缩放 0.5x–4x，放大后单指拖动。
 * [allowParentScroll] 为 true 时（如放在 Pager 里），未放大的单指手势不消费，交给外层翻页。
 */
@Composable
fun ZoomableImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    allowParentScroll: Boolean = false
) {
    var scale by remember(model) { mutableStateOf(1f) }
    var offsetX by remember(model) { mutableStateOf(0f) }
    var offsetY by remember(model) { mutableStateOf(0f) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .pointerInput(allowParentScroll) {
                    detectZoomAndPan(
                        consumeSingleFinger = !allowParentScroll,
                        canPan = { scale > 1f }
                    ) { zoom, pan ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentScale = contentScale
        )
    }
}

/**
 * 缩放/拖动手势：接管双指手势；单指手势是否消费由 [consumeSingleFinger] 与 [canPan] 决定，
 * 这样放在 Pager 内时左右滑动仍可翻页。
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    consumeSingleFinger: Boolean,
    canPan: () -> Boolean,
    onTransform: (zoom: Float, pan: Offset) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            val takeOver = pressed > 1 || canPan() || consumeSingleFinger
            if (takeOver && (zoom != 1f || pan != Offset.Zero)) {
                onTransform(zoom, pan)
                event.changes.forEach { it.consume() }
            }
            if (pressed == 0) break
        }
    }
}
