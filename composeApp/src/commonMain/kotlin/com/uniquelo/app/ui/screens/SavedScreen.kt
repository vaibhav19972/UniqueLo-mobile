package com.uniquelo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uniquelo.app.model.Product
import com.uniquelo.app.ui.components.ProductCard
import com.uniquelo.app.ui.components.UiIcon
import com.uniquelo.app.ui.theme.Palette

@Composable
fun SavedScreen(
    savedProducts: List<Product>,
    onBack: () -> Unit,
    onOpenProduct: (Product) -> Unit,
    onBrowse: () -> Unit,
    onToggleSaved: (Product) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(Palette.Bone)) {
        Header(
            count = savedProducts.size,
            onBack = onBack,
        )

        if (savedProducts.isEmpty()) {
            EmptySaved(
                onBrowse = onBrowse,
                modifier = Modifier.weight(1f),
            )
        } else {
            val rows = savedProducts.chunked(2)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
            ) {
                items(items = rows, key = { row -> row.joinToString("|") { it.slug } }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        row.forEach { product ->
                            ProductCard(
                                product = product,
                                onClick = { onOpenProduct(product) },
                                saved = true,
                                onToggleSaved = { onToggleSaved(product) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    count: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, Palette.ControlLine, RoundedCornerShape(2.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Back"
                }
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(UiIcon.ArrowLeft, modifier = Modifier.size(18.dp), tint = Palette.Ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SAVED PIECES",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "$count ${if (count == 1) "piece" else "pieces"}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
            )
        }
    }
    HorizontalDivider(color = Palette.Line)
}

@Composable
private fun EmptySaved(
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(Palette.Sand),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(UiIcon.Heart, modifier = Modifier.size(46.dp), tint = Palette.Ink)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Nothing saved yet.",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap the heart on any piece to curate your private collection.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Rust)
                .semantics { role = Role.Button }
                .clickable(onClick = onBrowse)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SHOP NEW ARRIVALS",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.OnAccent,
            )
        }
    }
}
