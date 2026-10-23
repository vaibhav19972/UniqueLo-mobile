package com.uniquelo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uniquelo.app.model.Product
import com.uniquelo.app.ui.theme.Palette

@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
    ) {
        Box {
            ProductVisual(
                product = product,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f),
                corner = 3.dp,
            )
            val tag = product.editionLabel ?: product.categoryLabel
            EditionTag(
                text = tag,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
            if (!product.hasStock) {
                Text(
                    text = "SOLD OUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Paper,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Palette.Ink.copy(alpha = 0.88f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = product.name,
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Ink,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${product.universeName} · ${product.categoryLabel}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = product.priceLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Ink,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (product.reviewCount > 0) {
                "★ ${product.ratingLabel} · ${product.reviewCount} reviews"
            } else {
                "New arrival"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
    }
}

@Composable
fun EditionTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Ink,
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Paper.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
