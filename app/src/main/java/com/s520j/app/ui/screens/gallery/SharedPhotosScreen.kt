package com.s520j.app.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.s520j.app.domain.heart.PhotoFeed
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PhotoPagerDialog
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

@Composable
fun SharedPhotosScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val records by remember { vm.allContents() }.collectAsState(initial = emptyList())
    val photos = remember(records) { PhotoFeed.sharedPhotos(records) }
    val images = remember(photos) { photos.map { it.image } }
    var viewingIndex by remember { mutableStateOf<Int?>(null) }

    PageContainer(scrollable = false) {
        BackBar(title = "共同照片", onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${photos.size} 张照片",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(14.dp))

        if (photos.isEmpty()) {
            SoftCard {
                EmptyState(text = "还没有共同照片")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
            ) {
                itemsIndexed(
                    items = photos,
                    key = { _, photo -> photo.key }
                ) { index, photo ->
                    AsyncImage(
                        model = photo.image,
                        contentDescription = "第 ${index + 1} 张照片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(S520JColors.AccentPink)
                            .clickable { viewingIndex = index },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }

    viewingIndex?.let { index ->
        PhotoPagerDialog(
            images = images,
            initialIndex = index,
            onDismiss = { viewingIndex = null }
        )
    }
}
