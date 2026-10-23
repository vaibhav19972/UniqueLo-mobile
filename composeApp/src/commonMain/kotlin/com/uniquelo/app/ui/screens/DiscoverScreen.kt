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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Universe
import com.uniquelo.app.state.CatalogStore
import com.uniquelo.app.state.NewsletterStore
import com.uniquelo.app.state.SubscribeState
import com.uniquelo.app.ui.components.ProductCard
import com.uniquelo.app.ui.components.ProductVisual
import com.uniquelo.app.ui.components.UiIcon
import com.uniquelo.app.ui.components.parseHexColor
import com.uniquelo.app.ui.theme.Palette

@Composable
fun DiscoverScreen(
    catalog: CatalogStore,
    newsletter: NewsletterStore,
    bagCount: Int,
    onOpenBag: () -> Unit,
    onOpenProduct: (Product) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val results = catalog.results
    val searching = catalog.query.isNotBlank()
    val featured = catalog.featuredFor(results)
    val rows = results.chunked(2)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Header(
                bagCount = bagCount,
                onBagClick = onOpenBag,
            )
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .background(Palette.Rust),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "THE AUTUMN EDIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Rust,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Wearable worlds.\nStitched stories.",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, lineHeight = 41.sp),
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Limited-run embroidery from independent craft clusters — " +
                        "three universes, one wardrobe.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(22.dp))
                SearchField(
                    value = catalog.query,
                    onValueChange = { catalog.query = it },
                    onClear = { catalog.query = "" },
                )
            }
        }

        when {
            catalog.isLoading -> item { LoadingBlock() }
            catalog.loadError != null -> item {
                ErrorBlock(message = catalog.loadError.orEmpty(), onRetry = { catalog.retry() })
            }

            else -> {
                if (catalog.syncError != null) {
                    item {
                        SyncBanner(
                            message = catalog.syncError.orEmpty(),
                            onRetry = { catalog.retry() },
                        )
                    }
                }

                if (!searching && catalog.universes.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(22.dp))
                        UniverseSection(
                            universes = catalog.universes,
                            selectedSlug = catalog.universeSlug,
                            onSelect = { catalog.selectUniverse(it) },
                            onClear = { catalog.selectUniverse(null) },
                        )
                    }
                }

                if (!searching) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        CategoryChips(
                            selected = catalog.categorySlug,
                            categories = catalog.categories.map { it.slug to it.label },
                            onSelect = { catalog.selectCategory(it) },
                        )
                    }
                }

                if (!searching && featured != null) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        FeaturedCard(
                            product = featured,
                            onClick = { onOpenProduct(featured) },
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 30.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (searching) "Results" else "New arrivals",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Palette.Ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "${results.size} ${if (results.size == 1) "piece" else "pieces"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Muted,
                        )
                    }
                }

                if (results.isEmpty()) {
                    item { EmptySearch(onReset = { catalog.resetFilters() }) }
                } else {
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
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item { NewsletterBlock(newsletter) }
                item { ManifestoFooter() }
            }
        }
    }
}

@Composable
private fun Header(
    bagCount: Int,
    onBagClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .size(10.dp)
                        .background(Palette.Rust),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Unique",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.Ink,
                )
                Text(
                    text = "Lo",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.Rust,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "EMBROIDERED WORLDS · LIMITED RUNS",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = Palette.Muted,
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Paper)
                .border(1.dp, Palette.ControlLine, RoundedCornerShape(2.dp))
                .semantics { contentDescription = "Open bag" }
                .clickable(onClick = onBagClick),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(UiIcon.Bag, modifier = Modifier.size(21.dp), tint = Palette.Ink)
            if (bagCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Palette.Rust),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (bagCount > 9) "9+" else bagCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Palette.OnAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Paper)
            .border(1.dp, Palette.ControlLine, RoundedCornerShape(2.dp))
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(UiIcon.Search, modifier = Modifier.size(18.dp), tint = Palette.Muted)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Search pieces, motifs or crafts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Muted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search the catalogue" },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Rust),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .semantics { contentDescription = "Clear search" }
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                UiIcon(UiIcon.Close, modifier = Modifier.size(15.dp), tint = Palette.Muted)
            }
        }
    }
}

@Composable
private fun UniverseSection(
    universes: List<Universe>,
    selectedSlug: String?,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CHOOSE A WORLD",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
            )
            if (selectedSlug != null) {
                Text(
                    text = "ALL WORLDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Rust,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 4.dp, vertical = 17.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = universes, key = { it.slug }) { universe ->
                UniverseCard(
                    universe = universe,
                    selected = universe.slug == selectedSlug,
                    onClick = { onSelect(universe.slug) },
                )
            }
        }
    }
}

@Composable
private fun UniverseCard(
    universe: Universe,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = parseHexColor(universe.theme.bg, Palette.Olive)
    val ink = parseHexColor(universe.theme.ink, Palette.Paper)
    val accent = parseHexColor(universe.theme.accent, Palette.Lime)

    Column(
        modifier = Modifier
            .width(258.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Palette.Rust else bg.copy(alpha = 0.6f),
                shape = RoundedCornerShape(2.dp),
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .semantics {
                contentDescription = "Filter by ${universe.name}"
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = universe.name,
                style = MaterialTheme.typography.titleLarge,
                color = ink,
            )
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Text(
            text = universe.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.86f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = universe.dropCadence?.uppercase() ?: "SEASONAL",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                UiIcon(UiIcon.Check, modifier = Modifier.size(14.dp), tint = accent)
            }
        }
    }
}

@Composable
private fun CategoryChips(
    selected: String?,
    categories: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CategoryChip(
                label = "ALL",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(items = categories, key = { it.first }) { (slug, label) ->
            CategoryChip(
                label = label.uppercase(),
                selected = selected == slug,
                onClick = { onSelect(slug) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) Palette.Paper else Palette.Ink,
        maxLines = 1,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) Palette.Ink else Palette.Paper)
            .border(
                width = 1.dp,
                color = if (selected) Palette.Ink else Palette.ControlLine,
                shape = RoundedCornerShape(2.dp),
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
    )
}

@Composable
private fun FeaturedCard(
    product: Product,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(420.dp)
            .clip(RoundedCornerShape(2.dp))
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
    ) {
        ProductVisual(
            product = product,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.25f to Color.Transparent,
                        1f to Palette.ImageScrim.copy(alpha = 0.92f),
                    ),
                ),
        )

        Text(
            text = "EDITOR'S PICK",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = Palette.OnAccent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .clip(RoundedCornerShape(0.dp))
                .background(Palette.Rust)
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )

        product.editionLabel?.let { edition ->
            Text(
                text = edition.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.OnImage,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .background(Palette.ImageScrim.copy(alpha = 0.72f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = "${product.universeName} · ${product.categoryLabel}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.OnImage.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.OnImage,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${product.craftCluster ?: product.universeName} · ${product.priceLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.OnImage.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Palette.Rust),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "VIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.OnImage,
                    )
                    Spacer(Modifier.width(2.dp))
                    UiIcon(UiIcon.ChevronRight, modifier = Modifier.size(14.dp), tint = Palette.OnImage)
                }
            }
        }
    }
}

@Composable
private fun NewsletterBlock(newsletter: NewsletterStore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 30.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.InverseSurface)
            .padding(20.dp),
    ) {
        Text(
            text = "THE NEXT DROP, FIRST",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = Palette.InverseAccent,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "One letter per world.\nNo noise, no restocks.",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.OnInverse,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Join the list and hear when a batch opens.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.OnInverse.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(18.dp))

        when (val state = newsletter.subscribeState) {
            is SubscribeState.Success -> {
                Row(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Palette.InverseAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        UiIcon(UiIcon.Check, modifier = Modifier.size(13.dp), tint = Palette.InverseSurface)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "You're on the list. Watch your inbox.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.OnInverse,
                    )
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, Palette.OnInverse.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (newsletter.subscribeEmail.isEmpty()) {
                            Text(
                                text = "you@example.com",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.OnInverse.copy(alpha = 0.7f),
                            )
                        }
                        BasicTextField(
                            value = newsletter.subscribeEmail,
                            onValueChange = {
                                newsletter.subscribeEmail = it
                                if (newsletter.subscribeState is SubscribeState.Error) {
                                    newsletter.clearSubscribeError()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Email address" },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.OnInverse),
                            cursorBrush = SolidColor(Palette.InverseAccent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { newsletter.subscribe() }),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Palette.InverseAccent)
                            .semantics { role = Role.Button }
                            .clickable(enabled = state !is SubscribeState.Submitting) {
                                newsletter.subscribe()
                            }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state is SubscribeState.Submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Palette.InverseSurface,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "NOTIFY ME",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.InverseSurface,
                            )
                        }
                    }
                }
                if (state is SubscribeState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.InverseAccent,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Palette.Rust, strokeWidth = 2.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Opening the atelier…",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
    }
}

@Composable
private fun SyncBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Sand)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "RETRY",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Rust,
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(2.dp))
            .background(Palette.Paper)
            .padding(18.dp),
    ) {
        Text(
            text = "The atelier is offline.",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "TRY AGAIN",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.OnAccent,
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Rust)
                .semantics { role = Role.Button }
                .clickable(onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun EmptySearch(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No pieces in this corner of the archive.",
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try another world, a motif or a broader category.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "CLEAR FILTERS",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.OnAccent,
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Rust)
                .semantics { role = Role.Button }
                .clickable(onClick = onReset)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun ManifestoFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp),
    ) {
        HorizontalDivider(color = Palette.Line)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "UNIQUELO / EST. 2026",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Every drop is embroidered in small batches with named craft clusters. " +
                "When a batch closes, the motif is retired.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink,
        )
    }
}
