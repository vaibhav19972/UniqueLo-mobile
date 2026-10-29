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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniquelo.app.model.LocalOrderSummary
import com.uniquelo.app.model.OrderDetails
import com.uniquelo.app.model.formatCents
import com.uniquelo.app.state.OrderLookupState
import com.uniquelo.app.state.OrdersStore
import com.uniquelo.app.ui.components.UiIcon
import com.uniquelo.app.ui.theme.Palette

@Composable
fun OrdersScreen(
    ordersStore: OrdersStore,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
) {
    var lookupId by remember { mutableStateOf("") }
    var lookupEmail by remember { mutableStateOf("") }

    val recentOrders = ordersStore.orders
    val lookupState = ordersStore.lookupState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bone),
    ) {
        Header(onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            // Live Details Card (when looked up)
            when (lookupState) {
                is OrderLookupState.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Palette.Rust,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Retrieving archival needlecraft records…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Muted,
                            )
                        }
                    }
                }

                is OrderLookupState.Error -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Palette.Rust.copy(alpha = 0.1f))
                            .border(1.dp, Palette.Rust.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "Notice: ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Palette.Rust,
                            )
                            Text(
                                text = lookupState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Ink,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                is OrderLookupState.Success -> item {
                    OrderDetailsCard(
                        details = lookupState.details,
                        onDismiss = { ordersStore.clearLookup() },
                    )
                    Spacer(Modifier.height(20.dp))
                }

                OrderLookupState.Idle -> Unit
            }

            // Recent Commissions Section
            if (recentOrders.isNotEmpty()) {
                item {
                    Text(
                        text = "RECENT COMMISSIONS (${recentOrders.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                items(items = recentOrders, key = { it.id }) { summary ->
                    RecentOrderRow(
                        summary = summary,
                        onClick = {
                            ordersStore.lookupOrder(summary.id, summary.email)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Palette.Line)
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Guest Lookup Form
            item {
                Text(
                    text = "ARCHIVAL ORDER LOOKUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Track any order using your Order ID and email.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(14.dp))

                LookupField(
                    label = "Order ID",
                    value = lookupId,
                    placeholder = "e.g. 550e8400-e29b-41d4-a716-446655440000",
                    onValueChange = { lookupId = it },
                )
                Spacer(Modifier.height(12.dp))
                LookupField(
                    label = "Email Address",
                    value = lookupEmail,
                    placeholder = "collector@domain.com",
                    keyboardType = KeyboardType.Email,
                    onValueChange = { lookupEmail = it },
                )
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Palette.Ink)
                        .semantics { role = Role.Button }
                        .clickable {
                            ordersStore.lookupOrder(lookupId, lookupEmail)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "TRACK COMMISSION",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Paper,
                    )
                }

                if (recentOrders.isEmpty() && lookupState is OrderLookupState.Idle) {
                    Spacer(Modifier.height(36.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.dp, Palette.ControlLine, RoundedCornerShape(2.dp))
                            .clickable(onClick = onBrowse),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "EXPLORE THE ATELIER",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(UiIcon.ArrowLeft, modifier = Modifier.size(20.dp), tint = Palette.Ink)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "COMMISSIONS & TRACKING",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Rust,
            )
            Text(
                text = "Order Vault",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

@Composable
private fun RecentOrderRow(
    summary: LocalOrderSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(2.dp))
            .background(Palette.Paper)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.code,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Palette.Ink,
            )
            Text(
                text = formatCents(summary.totalCents),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Palette.Rust,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${summary.itemCount} piece(s) · ${summary.email}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Text(
                text = "TRACK →",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Palette.Ink,
            )
        }
    }
}

@Composable
private fun OrderDetailsCard(
    details: OrderDetails,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Palette.Rust.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .background(Palette.Paper)
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "COMMISSION RECORD",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Rust,
                )
                Text(
                    text = details.code,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.Ink,
                )
            }
            Text(
                text = details.status.replace("-", " ").uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Palette.Paper,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.Rust)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Collector: ${details.customerName} (${details.customerEmail})",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Palette.Line)
        Spacer(Modifier.height(12.dp))

        Text(
            text = "PIECES IN COMMISSION",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(8.dp))

        details.items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = Palette.Ink,
                    )
                    Text(
                        text = "${item.color} · ${item.size} · Qty ${item.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                Text(
                    text = formatCents(item.priceCents * item.quantity),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Ink,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Palette.Line)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Total Amount", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Text(
                text = formatCents(details.totalCents),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Palette.Rust,
            )
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, Palette.ControlLine, RoundedCornerShape(2.dp))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "DISMISS RECORD",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun LookupField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Paper)
                .border(1.dp, Palette.ControlLine, RoundedCornerShape(2.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Rust),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next,
                ),
            )
        }
    }
}
