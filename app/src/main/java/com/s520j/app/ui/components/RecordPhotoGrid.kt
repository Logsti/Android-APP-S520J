package com.s520j.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.s520j.app.domain.heart.GalleryPhotoLayout

/** 记录图片宫格：单图铺满，2/3/4 图沿用照片墙布局，可逐图响应点击。 */
@Composable
fun RecordPhotoGrid(
    images: List<String>,
    title: String,
    modifier: Modifier = Modifier,
    onImageClick: ((Int) -> Unit)? = null
) {
    val visibleImages = images.take(GalleryPhotoLayout.MAX)
    if (visibleImages.isEmpty()) return
    if (visibleImages.size == 1) {
        AsyncImage(
            model = visibleImages.first(),
            contentDescription = title,
            modifier = modifier.then(
                if (onImageClick == null) Modifier else Modifier.clickable { onImageClick(0) }
            ),
            contentScale = ContentScale.Crop
        )
        return
    }

    val filled = GalleryPhotoLayout.cells(visibleImages.size)
        .zip(visibleImages.withIndex())
        .toMap()
    val gap = 2.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            when {
                filled.containsKey(0) && filled.containsKey(1) -> {
                    PhotoCell(filled.getValue(0), title, Modifier.weight(1f), onImageClick)
                    PhotoCell(filled.getValue(1), title, Modifier.weight(1f), onImageClick)
                }
                filled.containsKey(0) -> {
                    PhotoCell(filled.getValue(0), title, Modifier.weight(1f), onImageClick)
                    Spacer(Modifier.weight(1f))
                }
                filled.containsKey(1) -> {
                    Spacer(Modifier.weight(0.5f))
                    PhotoCell(filled.getValue(1), title, Modifier.weight(1f), onImageClick)
                    Spacer(Modifier.weight(0.5f))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            (2..3).forEach { cell ->
                val photo = filled[cell]
                if (photo != null) {
                    PhotoCell(photo, title, Modifier.weight(1f), onImageClick)
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(
    photo: IndexedValue<String>,
    title: String,
    modifier: Modifier,
    onImageClick: ((Int) -> Unit)?
) {
    AsyncImage(
        model = photo.value,
        contentDescription = title,
        modifier = modifier
            .fillMaxHeight()
            .then(if (onImageClick == null) Modifier else Modifier.clickable { onImageClick(photo.index) }),
        contentScale = ContentScale.Crop
    )
}
