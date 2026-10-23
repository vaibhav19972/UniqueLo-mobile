package com.uniquelo.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uniquelo.app.ui.components.UiIcon
import com.uniquelo.app.ui.theme.Palette

@Composable
fun ProfileScreen(onBrowse: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    ) {
        item {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Ink,
            )
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(Palette.Lime),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "UL",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Palette.Ink,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guest",
                        style = MaterialTheme.typography.titleLarge,
                        color = Palette.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Sign in to sync saved pieces",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Muted,
                    )
                }
                Text(
                    text = "SOON",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(Palette.Sand)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Palette.Line)
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Stat(value = "0", label = "Orders", modifier = Modifier.weight(1f))
                Stat(value = "0", label = "Saved", modifier = Modifier.weight(1f))
                Stat(value = "0", label = "Alerts", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Rust)
                    .padding(18.dp),
            ) {
                Column {
                    Text(
                        text = "CRAFT WITH UNIQUELO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Paper.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Embroider in a cluster?\nPut your craft in the next drop.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.Paper,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "APPLY AS A MAKER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(Palette.Lime)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
        }

        item {
            ProfileRow("Orders")
            ProfileRow("Saved pieces")
            ProfileRow("Addresses")
            ProfileRow("Payment methods")
            ProfileRow("Preferences")
            ProfileRow("Help & support", last = true)
        }

        item {
            Spacer(Modifier.height(26.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Ink)
                    .clickable(onClick = onBrowse),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "BACK TO THE EDIT",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Lime,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "UniqueLo MVP · v1.1.0\nKotlin Multiplatform + Compose + Supabase",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = Palette.Ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted,
        )
    }
}

@Composable
private fun ProfileRow(label: String, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 17.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Ink,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SOON",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                )
                Spacer(Modifier.width(8.dp))
                UiIcon(UiIcon.ChevronRight, modifier = Modifier.size(14.dp), tint = Palette.Line)
            }
        }
        if (!last) HorizontalDivider(color = Palette.Line)
    }
}
