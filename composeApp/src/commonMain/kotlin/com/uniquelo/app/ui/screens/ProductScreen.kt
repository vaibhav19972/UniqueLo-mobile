package com.uniquelo.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Review
import com.uniquelo.app.model.Variant
import com.uniquelo.app.model.formatCents
import com.uniquelo.app.state.BagStore
import com.uniquelo.app.ui.components.ProductVisual
import com.uniquelo.app.ui.components.UiIcon
import com.uniquelo.app.ui.components.parseHexColor
import com.uniquelo.app.ui.productSharedElement
import com.uniquelo.app.ui.theme.Palette

@Composable
fun ProductScreen(
    product: Product,
    bag: BagStore,
    onBack: () -> Unit,
    onOpenBag: () -> Unit,
) {
    val colors = product.colors
    var selectedColor by rememberSaveable(product.slug) { mutableStateOf(colors.firstOrNull()?.name) }
    var selectedSize by rememberSaveable(product.slug) { mutableStateOf<String?>(null) }
    var added by rememberSaveable(product.slug) { mutableStateOf(false) }

    val selectedVariant = product.variants.firstOrNull {
        it.color == selectedColor && it.size == selectedSize
    }
    val sizesForColor = product.variants.filter { it.color == selectedColor }

    Column(modifier = Modifier.fillMaxSize().background(Palette.Bone)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Gallery(product = product, onBack = onBack)

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 22.dp, bottom = 30.dp),
            ) {
            Text(
                text = "${product.universeName} · ${product.categoryLabel}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, lineHeight = 39.sp),
                color = Palette.Ink,
            )
            if (product.subtitle.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = product.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Muted,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "★ ${product.ratingLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Ink,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (product.reviewCount > 0) {
                        "(${product.reviewCount} reviews)"
                    } else {
                        "New arrival"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
                product.soldCount?.let { sold ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "· $sold sold",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = selectedVariant?.let { formatCents(it.priceCents) } ?: product.priceLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Ink,
                )
                selectedVariant?.compareAtPriceCents?.takeIf { it > selectedVariant.priceCents }?.let { compare ->
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = formatCents(compare),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Palette.Line)
            Spacer(Modifier.height(18.dp))

            if (product.description.isNotBlank()) {
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(22.dp))
            }

            if (colors.isNotEmpty()) {
                ColorSelector(
                    colors = colors.map { it.name to it.hex },
                    selected = selectedColor,
                    onSelect = { name ->
                        selectedColor = name
                        selectedSize = null
                        added = false
                    },
                )
                Spacer(Modifier.height(20.dp))
            }

            if (sizesForColor.isNotEmpty()) {
                Text(
                    text = "SIZE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sizesForColor.forEach { variant ->
                        SizeChip(
                            variant = variant,
                            selected = variant.size == selectedSize,
                            onClick = {
                                selectedSize = variant.size
                                added = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val stockText = when {
                    selectedVariant == null -> "Select a size to see availability."
                    selectedVariant.inStock -> selectedVariant.statusLabel
                    else -> "This size is sold out."
                }
                Text(
                    text = stockText,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        selectedVariant?.inStock == true && selectedVariant.lowStock -> Palette.Rust
                        selectedVariant?.inStock == true -> Palette.Muted
                        else -> Palette.Muted
                    },
                )
                Spacer(Modifier.height(22.dp))
            }

            ProductStory(product)

            Spacer(Modifier.height(22.dp))
            ReviewsSection(product)
            }
        }

        PurchaseBar(
            product = product,
            selectedVariant = selectedVariant,
            added = added,
            onAdd = {
                selectedVariant?.let { variant ->
                    bag.add(product, variant)
                    added = true
                }
            },
            onViewBag = onOpenBag,
        )
    }
}

@Composable
private fun Gallery(
    product: Product,
    onBack: () -> Unit,
) {
    val images = product.remoteImages
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .productSharedElement(product.slug),
        ) {
            if (images.isEmpty()) {
                ProductVisual(
                    product = product,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val pagerState = rememberPagerState(pageCount = { images.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val image = images[page]
                    ProductVisual(
                        product = product,
                        imageUrl = image.src,
                        contentDescription = image.alt.ifBlank { product.name },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (images.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Palette.ImageScrim.copy(alpha = 0.45f))
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        images.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == index) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (pagerState.currentPage == index) {
                                            Palette.Rust
                                        } else {
                                            Palette.OnImage.copy(alpha = 0.55f)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Palette.Paper.copy(alpha = 0.94f))
                .semantics { contentDescription = "Back" }
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(UiIcon.ArrowLeft, modifier = Modifier.size(20.dp), tint = Palette.Ink)
        }

        product.editionLabel?.let { edition ->
            Text(
                text = edition.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.OnAccent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.Rust)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ColorSelector(
    colors: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Text(
        text = "COLOUR · ${selected?.uppercase() ?: ""}",
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(9.dp))
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { (name, hex) ->
            val isSelected = name == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Palette.Ink else Palette.ControlLine,
                        shape = CircleShape,
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex, Palette.Sand))
                    .border(1.dp, Palette.Ink.copy(alpha = 0.18f), CircleShape)
                    .semantics { contentDescription = "Colour $name" }
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(name) },
                        role = Role.RadioButton,
                    ),
            )
        }
    }
}

@Composable
private fun SizeChip(
    variant: Variant,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> Palette.Ink
        variant.inStock -> Palette.Paper
        else -> Palette.Sand
    }
    val fg = when {
        selected -> Palette.Paper
        variant.inStock -> Palette.Ink
        else -> Palette.Muted
    }
    Box(
        modifier = Modifier
            .height(48.dp)
            .defaultMinSize(minWidth = 56.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) Palette.Ink else Palette.ControlLine,
                shape = RoundedCornerShape(2.dp),
            )
            .selectable(
                selected = selected,
                enabled = variant.inStock,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = variant.size,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            textDecoration = if (!variant.inStock) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun ProductStory(product: Product) {
    Text(
        text = "THE MAKING",
        style = MaterialTheme.typography.labelSmall,
        color = Palette.Muted,
    )
    Spacer(Modifier.height(10.dp))

    val embroidery = product.embroidery
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Paper)
            .border(1.dp, Palette.Line, RoundedCornerShape(2.dp))
            .padding(16.dp),
    ) {
        Text(
            text = embroidery?.techniqueLabel
                ?: product.craftCluster
                ?: "Hand-finished in small batches",
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = embroidery?.motifStory
                ?: product.motif?.meaning
                ?: "Every piece is finished by a named craft cluster and released in a numbered batch.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink.copy(alpha = 0.82f),
        )

        if (embroidery != null && embroidery.placement.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                embroidery.placement.take(3).forEach { placement ->
                    Text(
                        text = placement.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Ink,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.dp, Palette.Ink.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Palette.Ink.copy(alpha = 0.12f))
        Spacer(Modifier.height(12.dp))

        embroidery?.artisanHours?.let {
            DetailRow("Artisan hours", "$it hours")
        }
        product.craftCluster?.let { DetailRow("Craft cluster", it) }
        product.craftRegion?.let { DetailRow("Region", it) }
        embroidery?.threadComposition?.takeIf { it.isNotBlank() }?.let {
            DetailRow("Thread", it)
        }
        product.fabricGsm?.let { DetailRow("Fabric", it) }
        product.motif?.let { motif ->
            DetailRow("Motif", motif.name)
        }
    }

    Spacer(Modifier.height(18.dp))

    if (product.material.isNotEmpty()) {
        Text(
            text = "MATERIALS",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(8.dp))
        product.material.forEach { line ->
            Bullet(line)
        }
        Spacer(Modifier.height(14.dp))
    }

    if (product.care.isNotEmpty()) {
        Text(
            text = "CARE",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(8.dp))
        product.care.forEach { line ->
            Bullet(line)
        }
        Spacer(Modifier.height(14.dp))
    }

    product.fit?.takeIf { it.isNotBlank() }?.let { fit ->
        DetailRow("Fit", fit)
    }
    product.gender?.let { gender ->
        DetailRow("Cut", gender.replaceFirstChar { it.uppercase() })
    }
    product.garmentType?.let { type ->
        DetailRow("Garment", type.replaceFirstChar { it.uppercase() })
    }
    product.collectionName?.let { collection ->
        DetailRow("Collection", collection)
    }
    DetailRow("Ships", "3–5 working days, tracked")
    DetailRow("Returns", "14 days, unworn")

    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Palette.Paper)
            .border(1.dp, Palette.Line, RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Palette.Rust),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildString {
                append("Released in a numbered batch")
                product.editionLabel?.let { append(" · $it") }
                append(". Once this run closes, the motif is retired.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink,
        )
    }
}

@Composable
private fun ReviewsSection(product: Product) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "REVIEWS",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Text(
            text = if (product.reviews.isEmpty()) "None yet" else "${product.reviews.size} recent",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
    }
    Spacer(Modifier.height(10.dp))

    if (product.reviews.isEmpty()) {
        Text(
            text = "No reviews have been written for this piece yet. The first one is yours to leave.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
    } else {
        product.reviews.forEachIndexed { index, review ->
            ReviewCard(review)
            if (index != product.reviews.lastIndex) {
                HorizontalDivider(
                    color = Palette.Line,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(review: Review) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "★".repeat(review.rating.coerceIn(0, 5)) + "☆".repeat((5 - review.rating).coerceIn(0, 5)),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Rust,
            )
            if (review.verified) {
                Text(
                    text = "VERIFIED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Rust,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = review.title.ifBlank { "A note from the wardrobe" },
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Ink,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = review.comment,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "— ${review.author}",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
    }
}

@Composable
private fun PurchaseBar(
    product: Product,
    selectedVariant: Variant?,
    added: Boolean,
    onAdd: () -> Unit,
    onViewBag: () -> Unit,
) {
    val canAdd = selectedVariant?.inStock == true
    val buttonColor by animateColorAsState(
        when {
            added -> Palette.Ink
            canAdd -> Palette.Rust
            else -> Palette.Sand
        },
    )
    val labelColor = when {
        added -> Palette.Paper
        canAdd -> Palette.OnAccent
        else -> Palette.Muted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Paper)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (selectedVariant == null) "FROM" else "PRICE",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
            )
            Text(
                text = selectedVariant?.let { formatCents(it.priceCents) }
                    ?: formatCents(product.minPriceCents),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
            )
        }
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .weight(1.6f)
                .height(54.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(buttonColor)
                .semantics { role = Role.Button }
                .clickable(enabled = added || canAdd) {
                    if (added) onViewBag() else onAdd()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    added -> "VIEW BAG"
                    selectedVariant == null -> "SELECT A SIZE"
                    !selectedVariant.inStock -> "SOLD OUT"
                    else -> "ADD TO BAG"
                },
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
            modifier = Modifier.width(102.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Rust,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink,
        )
    }
}
