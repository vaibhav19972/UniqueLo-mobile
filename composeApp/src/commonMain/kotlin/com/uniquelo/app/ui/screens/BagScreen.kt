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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.formatCents
import com.uniquelo.app.state.BagStore
import com.uniquelo.app.ui.components.ProductVisual
import com.uniquelo.app.ui.components.UiIcon
import com.uniquelo.app.ui.theme.Palette

@Composable
fun BagScreen(
    bag: BagStore,
    onCheckout: () -> Unit,
    onBrowse: () -> Unit,
) {
    if (bag.isEmpty) {
        EmptyBag(onBrowse = onBrowse)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Your bag",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "${bag.bagCount} ${if (bag.bagCount == 1) "piece" else "pieces"}".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Palette.Line)
        }

        items(items = bag.lines, key = { it.variant.sku }) { line ->
            BagLine(
                line = line,
                onIncrement = { bag.increment(line.variant.sku) },
                onDecrement = { bag.decrement(line.variant.sku) },
                onRemove = { bag.remove(line.variant.sku) },
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SummaryCard(
                subtotalCents = bag.subtotalCents,
                onCheckout = onCheckout,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Reservations are written to the atelier's order book and held for 15 minutes. " +
                    "Every order ships in recycled, plastic-free packaging.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun BagLine(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ProductVisual(
                product = line.product,
                modifier = Modifier
                    .width(82.dp)
                    .height(104.dp),
                corner = 0.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${line.variant.color} · ${line.variant.size}".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = line.variant.sku.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuantityStepper(
                        quantity = line.quantity,
                        onIncrement = onIncrement,
                        onDecrement = onDecrement,
                    )
                    Text(
                        text = formatCents(line.lineTotalCents),
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Ink,
                    )
                }
            }
        }
        Text(
            text = "REMOVE",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .semantics { role = Role.Button }
                .clickable(onClick = onRemove)
                .padding(horizontal = 4.dp, vertical = 17.dp),
        )
        HorizontalDivider(color = Palette.Line)
    }
}

@Composable
private fun QuantityStepper(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Palette.ControlLine, RoundedCornerShape(2.dp))
            .background(Palette.Paper),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(UiIcon.Minus, label = "Decrease quantity", onClick = onDecrement)
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        StepperButton(UiIcon.Plus, label = "Increase quantity", onClick = onIncrement)
    }
}

@Composable
private fun StepperButton(icon: UiIcon, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(icon, modifier = Modifier.size(14.dp), tint = Palette.Ink)
    }
}

@Composable
private fun SummaryCard(
    subtotalCents: Int,
    onCheckout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(2.dp))
            .background(Palette.Paper)
            .padding(16.dp),
    ) {
        SummaryRow("Subtotal", formatCents(subtotalCents))
        Spacer(Modifier.height(8.dp))
        SummaryRow("Shipping", "Included")
        Spacer(Modifier.height(8.dp))
        SummaryRow("Tax", "Included in the atelier price")
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Palette.Line)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
            )
            Text(
                text = formatCents(subtotalCents),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
            )
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Rust)
                .semantics { role = Role.Button }
                .clickable(onClick = onCheckout),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "CHECKOUT",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.OnAccent,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "No payment is taken in this MVP. The order is saved as a reservation.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Ink,
        )
    }
}

@Composable
private fun EmptyBag(onBrowse: () -> Unit) {
    Column(
        modifier = Modifier
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
            UiIcon(UiIcon.Bag, modifier = Modifier.size(46.dp), tint = Palette.Ink)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Nothing in the wardrobe yet.",
            style = MaterialTheme.typography.headlineSmall,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Three worlds are open. Find the piece that belongs to yours.",
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
