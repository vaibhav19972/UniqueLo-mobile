package com.uniquelo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uniquelo.app.data.CheckoutForm
import com.uniquelo.app.data.PlacedOrder
import com.uniquelo.app.model.formatCents
import com.uniquelo.app.state.BagStore
import com.uniquelo.app.state.CheckoutStore
import com.uniquelo.app.state.OrderState
import com.uniquelo.app.ui.components.UiIcon
import com.uniquelo.app.ui.theme.Palette

@Composable
fun CheckoutScreen(
    checkout: CheckoutStore,
    bag: BagStore,
    onSubmit: (CheckoutForm) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    when (val state = checkout.orderState) {
        is OrderState.Success -> ConfirmationScreen(order = state.order, onFinish = onFinish)
        else -> CheckoutFormScreen(
            checkout = checkout,
            bag = bag,
            session = checkout.checkoutSession,
            onSubmit = onSubmit,
            onBack = onBack,
        )
    }
}

@Composable
private fun CheckoutFormScreen(
    checkout: CheckoutStore,
    bag: BagStore,
    session: Int,
    onSubmit: (CheckoutForm) -> Unit,
    onBack: () -> Unit,
) {
    // Keyed by the checkout session: rotation restores the half-typed form,
    // but a new checkout starts blank instead of reusing the previous
    // customer's details.
    var name by rememberSaveable(session) { mutableStateOf("") }
    var email by rememberSaveable(session) { mutableStateOf("") }
    var address by rememberSaveable(session) { mutableStateOf("") }
    var city by rememberSaveable(session) { mutableStateOf("") }
    var postal by rememberSaveable(session) { mutableStateOf("") }
    var country by rememberSaveable(session) { mutableStateOf("India") }
    var attempted by rememberSaveable(session) { mutableStateOf(false) }
    var formError by rememberSaveable(session) { mutableStateOf<String?>(null) }

    val submitting = checkout.orderState is OrderState.Submitting
    val emailRegex = remember { Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") }

    Column(modifier = Modifier.fillMaxSize().background(Palette.Bone)) {
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
                    .border(1.dp, Palette.Line, CircleShape)
                    .semantics { contentDescription = "Back" }
                    .clickable(enabled = !submitting, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                UiIcon(UiIcon.ArrowLeft, modifier = Modifier.size(18.dp), tint = Palette.Ink)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Checkout",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.Ink,
                )
                Text(
                    text = "SECURE RESERVATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            OrderSummary(bag = bag)

            Spacer(Modifier.height(24.dp))
            Text(
                text = "YOUR DETAILS",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The atelier uses these to hold your pieces and send a receipt.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(16.dp))

            LabelledField(
                label = "Full name",
                value = name,
                onValueChange = { name = it; formError = null },
                placeholder = "Your name",
                enabled = !submitting,
                invalid = attempted && name.isBlank(),
            )
            Spacer(Modifier.height(16.dp))
            LabelledField(
                label = "Email",
                value = email,
                onValueChange = { email = it; formError = null },
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                enabled = !submitting,
                invalid = attempted && !emailRegex.matches(email.trim()),
            )
            Spacer(Modifier.height(16.dp))
            LabelledField(
                label = "Shipping address",
                value = address,
                onValueChange = { address = it; formError = null },
                placeholder = "Street, building, landmark",
                enabled = !submitting,
                invalid = attempted && address.isBlank(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelledField(
                    label = "City",
                    value = city,
                    onValueChange = { city = it; formError = null },
                    placeholder = "City",
                    enabled = !submitting,
                    invalid = attempted && city.isBlank(),
                    modifier = Modifier.weight(1.4f),
                )
                LabelledField(
                    label = "PIN code",
                    value = postal,
                    onValueChange = { postal = it; formError = null },
                    placeholder = "000000",
                    keyboardType = KeyboardType.Number,
                    enabled = !submitting,
                    invalid = attempted && postal.isBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            LabelledField(
                label = "Country",
                value = country,
                onValueChange = { country = it; formError = null },
                placeholder = "Country",
                enabled = !submitting,
                invalid = attempted && country.isBlank(),
            )

            val error = formError ?: (checkout.orderState as? OrderState.Error)?.message
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Rust,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "No payment is taken. This creates a reservation in the atelier's order book.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Paper)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TOTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
                Text(
                    text = formatCents(bag.subtotalCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (submitting) Palette.Sand else Palette.Lime)
                    .clickable(enabled = !submitting) {
                        attempted = true
                        formError = null
                        when {
                            name.isBlank() -> formError = "Please add a name."
                            !emailRegex.matches(email.trim()) -> formError = "Enter a valid email address."
                            address.isBlank() -> formError = "Please add a shipping address."
                            city.isBlank() -> formError = "Please add a city."
                            postal.isBlank() -> formError = "Please add a PIN code."
                            country.isBlank() -> formError = "Please add a country."
                            else -> onSubmit(
                                CheckoutForm(
                                    name = name,
                                    email = email,
                                    addressLine = address,
                                    city = city,
                                    postalCode = postal,
                                    country = country,
                                ),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (submitting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Palette.Ink,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "RESERVING…",
                            style = MaterialTheme.typography.labelLarge,
                            color = Palette.Ink,
                        )
                    }
                } else {
                    Text(
                        text = "PLACE RESERVATION · ${formatCents(bag.subtotalCents)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderSummary(bag: BagStore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(4.dp))
            .background(Palette.Paper)
            .padding(16.dp),
    ) {
        Text(
            text = "ORDER SUMMARY",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(12.dp))
        bag.lines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.product.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${line.variant.color} · ${line.variant.size} × ${line.quantity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatCents(line.lineTotalCents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Ink,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Palette.Line)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Shipping",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
            )
            Text(
                text = "Included",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Ink,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
            )
            Text(
                text = formatCents(bag.subtotalCents),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
            )
        }
    }
}

@Composable
private fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    invalid: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (invalid) Palette.Rust else Palette.Muted,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Muted.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Ink),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next,
                ),
            )
        }
        HorizontalDivider(
            color = if (invalid) Palette.Rust else Palette.Line,
        )
    }
}

@Composable
private fun ConfirmationScreen(
    order: PlacedOrder,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bone)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(Palette.Lime),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(UiIcon.Check, modifier = Modifier.size(36.dp), tint = Palette.Ink)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Reserved.",
            style = MaterialTheme.typography.displayLarge,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "The atelier has your pieces. A receipt will go to ${order.email}.",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(4.dp))
                .background(Palette.Paper)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ORDER",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = order.code,
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Keep this code for your records.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.Ink)
                .clickable { onFinish() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "CONTINUE EXPLORING",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Lime,
            )
        }
    }
}
