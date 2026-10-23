package com.uniquelo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uniquelo.app.model.Product
import com.uniquelo.app.ui.theme.Palette

/**
 * Shows the product photograph when one is available, with the generated
 * artwork as a stable placeholder and error state.
 */
@Composable
fun ProductVisual(
    product: Product,
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    imageUrl: String? = product.primaryImageUrl,
    contentDescription: String? = product.name,
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Palette.Sand),
    ) {
        ProductArtwork(product = product, modifier = Modifier.fillMaxSize())
        if (!imageUrl.isNullOrBlank() && !failed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        }
    }
}
