package com.uniquelo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uniquelo.app.navigation.Destination
import com.uniquelo.app.navigation.destinationTabs
import com.uniquelo.app.ui.theme.Palette

@Composable
fun UniqueLoBottomBar(
    current: Destination,
    bagCount: Int,
    onSelect: (Destination) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Palette.Paper)) {
        HorizontalDivider(color = Palette.Line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinationTabs.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    selected = current == tab,
                    badge = if (tab == Destination.Bag) bagCount else 0,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

/** Tab-bar icons; overlays can never be passed here. */
private fun iconFor(tab: Destination): UiIcon = when (tab) {
    is Destination.Discover -> UiIcon.Discover
    is Destination.Bag -> UiIcon.Bag
    is Destination.Profile -> UiIcon.Person
    is Destination.Product,
    is Destination.Checkout,
    -> error("Tab bar icon requested for overlay destination $tab")
}

@Composable
private fun BottomBarItem(
    tab: Destination,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
) {
    val tint = if (selected) Palette.Ink else Palette.Muted
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            UiIcon(
                icon = iconFor(tab),
                modifier = Modifier.size(DefaultIconSize),
                tint = tint,
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-7).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Palette.Lime),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 9) "9+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Ink,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = tab.label.orEmpty().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 4.dp)
                .clip(CircleShape)
                .background(if (selected) Palette.Rust else Palette.Paper),
        )
    }
}
